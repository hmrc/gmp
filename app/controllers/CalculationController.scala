/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import com.google.inject.{Inject, Singleton}
import config.AppConfig
import models.HipCalcResult.{Failures, Success}
import connectors.*
import controllers.auth.GmpAuthAction
import events.ResultsEvent
import models.{CalculationRequest, CalculationResponse, GmpCalculationResponse, HipCalcResult, HipCalculationRequest}
import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{Action, ControllerComponents}
import repositories.CalculationRepository
import services.HipMappingService
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.LoggingUtils

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CalculationController @Inject()(desConnector: DesConnector,
                                      ifConnector: IFConnector,
                                      hipConnector: HipConnector,
                                      repository: CalculationRepository,
                                      authAction: GmpAuthAction,
                                      auditConnector: AuditConnector,
                                      hipMappingService: HipMappingService,
                                      cc: ControllerComponents,
                                      appConfig: AppConfig)
                                     (implicit val ec: ExecutionContext) extends BackendController(cc) with Logging {

  def requestCalculation(userId: String): Action[JsValue] = authAction.async(parse.json) {

    implicit request => {

      withJsonBody[CalculationRequest] { calculationRequest =>

        val result = repository.findByRequest(calculationRequest).flatMap {
          case Some(cr) =>
            sendResultsEvent(cr, cached = true, userId)
            Future.successful(Ok(Json.toJson(cr)))
          case None =>
            desConnector.getPersonDetails(calculationRequest.nino).flatMap {
              case DesGetHiddenRecordResponse =>
                val response = GmpCalculationResponse(
                  calculationRequest.firstForename + " " + calculationRequest.surname,
                  calculationRequest.nino,
                  calculationRequest.scon,
                  None,
                  None,
                  List(),
                  LOCKED,
                  None,
                  None,
                  None,
                  dualCalc = false,
                  calculationRequest.calctype.getOrElse(-1)
                )
                Future.successful(Ok(Json.toJson(response)))
              case _ =>
                handleNewCalculation(userId, calculationRequest).flatMap { response =>
                  for {
                    _ <- repository.insertByRequest(calculationRequest, response)
                    _ = sendResultsEvent(response, cached = false, userId)
                  } yield Ok(Json.toJson(response))
                }.recover {
                  case e: UpstreamErrorResponse if e.statusCode == 500 =>
                    logger.error(s"[CalculationController][requestCalculation] Internal Server Error")
                    logger.debug(s"[CalculationController][requestCalculation] Error details: ${LoggingUtils.redactError(e.getMessage)}")
                    InternalServerError(e.getMessage)
                }
            }
        }
        result.map {
          res => res
        }
      }
    }
  }

  private def handleNewCalculation(userId: String, calculationRequest: CalculationRequest)(implicit hc: HeaderCarrier): Future[GmpCalculationResponse] = {
    val connectorResult: Either[Future[HipCalcResult], Future[CalculationResponse]] =
      (appConfig.isIfsEnabled, appConfig.isHipEnabled) match {
        case (true, false) => Right(ifConnector.calculate(userId, calculationRequest))
        case (_, true) => Left(hipConnector.calculate(userId, HipCalculationRequest.from(calculationRequest)))
        case _ => Right(desConnector.calculate(userId, calculationRequest))
      }

    connectorResult match {
      case Left(hipCalc) =>
        hipCalc.map {
          case Success(success) => hipMappingService.mapSuccess(success, calculationRequest)
          case Failures(failures, status) => hipMappingService.mapFailures(failures, status, calculationRequest)
        }
      case Right(calc) =>
        calc.map(mapDesOrIfToGmp(_, calculationRequest))
    }
  }

  private def mapDesOrIfToGmp(c: CalculationResponse, req: CalculationRequest): GmpCalculationResponse = {
    GmpCalculationResponse.createFromCalculationResponse(c)(
      req.nino,
      req.scon,
      s"${req.firstForename} ${req.surname}",
      req.revaluationRate,
      req.revaluationDate,
      req.dualCalc.contains(1),
      req.calctype.getOrElse(-1)
    )
  }

  private def sendResultsEvent(response: GmpCalculationResponse, cached: Boolean, userId: String)(implicit hc: HeaderCarrier): Unit = {
    val idType = userId.take(1) match {
      case x if x.matches("[A-Z]") => "psa"
      case x if x.matches("[0-9]") => "psp"
    }
    val resultsEventResult = auditConnector.sendEvent(new ResultsEvent(
      !response.hasErrors, response.errorCodes, response.calcType, response.dualCalc, response.scon, cached, idType)
    )
    resultsEventResult.failed.foreach({
      (e: Throwable) => logger.warn("[CalculationController][sendResultsEvent] : resultsEventResult: " + e.getMessage, e)
    })
  }
}
