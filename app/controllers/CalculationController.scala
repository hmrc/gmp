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
import connectors.{DesConnector, DesGetHiddenRecordResponse, HipConnector, IFConnector}
import controllers.auth.GmpAuthAction
import events.ResultsEvent
import models._
import play.api.Logging
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents}
import repositories.CalculationRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CalculationController @Inject()(desConnector: DesConnector,
                                      ifConnector: IFConnector,
                                      hipConnector: HipConnector,
                                      repository: CalculationRepository,
                                      authAction: GmpAuthAction,
                                      auditConnector: AuditConnector,
                                      cc: ControllerComponents,
                                      val servicesConfig: ServicesConfig)
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
                val ifSwitch: Boolean = servicesConfig.getBoolean("ifs.enabled")
                val isHipEnabled: Boolean = servicesConfig.getBoolean("feature.hipIntegration")
                val result: Either[Future[HipCalculationResponse], Future[CalculationResponse]] = if (ifSwitch & !isHipEnabled) {
                  Right(ifConnector.calculate(userId, calculationRequest))
                } else if (isHipEnabled & !ifSwitch) {
                  Left(hipConnector.calculate(userId, HipCalculationRequest.from(calculationRequest)))
                } else {
                  Right(desConnector.calculate(userId, calculationRequest))
                }
                val transformedResult: Future[GmpCalculationResponse] = result match {
                  case Left(calculation) => calculation.map(c => GmpCalculationResponse.createFromHipResponse(c)(
                    calculationRequest.firstForename + " " + calculationRequest.surname,
                    Some(calculationRequest.revaluationRate.toString),
                    calculationRequest.revaluationDate,
                    calculationRequest.dualCalc.fold(false)(_ == 1),
                    calculationRequest.calctype.get,
                    calculationRequest.nino,
                    calculationRequest.scon
                  ))
                  case Right(calculation) => calculation.map(c => GmpCalculationResponse.createFromCalculationResponse(c)(
                    calculationRequest.nino, calculationRequest.scon, calculationRequest.firstForename + " " + calculationRequest.surname,
                    calculationRequest.revaluationRate,
                    calculationRequest.revaluationDate,
                    calculationRequest.dualCalc.fold(false)(_ == 1),
                    calculationRequest.calctype.get
                  ))
                }

                transformedResult.flatMap { transformedResultS =>
                  logger.debug(s"[CalculationController][transformedResult] : $transformedResultS")
                  for {
                    _ <- repository.insertByRequest(calculationRequest, transformedResultS)
                    _ = sendResultsEvent(transformedResultS, cached = false, userId)} yield Ok(Json.toJson(transformedResultS))
                }
            }.recover {
              case e: UpstreamErrorResponse if e.statusCode == 500 =>
                logger.error(s"[CalculateController][requestCalculation][transformedResult][ERROR:500] : ${e.getMessage}")
                InternalServerError(e.getMessage)
            }
        }
        result.map {
          res => res
        }
      }
    }
  }

  def sendResultsEvent(response: GmpCalculationResponse, cached: Boolean, userId: String)(implicit hc: HeaderCarrier): Unit = {
    val idType = userId.take(1) match {
      case x if x.matches("[A-Z]") => "psa"
      case x if x.matches("[0-9]") => "psp"
    }
    val resultsEventResult = auditConnector.sendEvent(new ResultsEvent(
      !response.hasErrors, response.errorCodes, response.calcType, response.dualCalc, response.scon, cached, idType)
    )
    resultsEventResult.failed.foreach({
      e: Throwable => logger.warn("[CalculationController][sendResultsEvent] : resultsEventResult: " + e.getMessage, e)
    })
  }
}