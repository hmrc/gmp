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
import connectors.{DesConnector, HipConnector}
import controllers.auth.GmpAuthAction
import models.{GmpValidateSconResponse, ValidateSconRequest}
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import repositories.ValidateSconRepository
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.LoggingUtils._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ValidateSconController @Inject()(desConnector: DesConnector,
                                       hipConnector: HipConnector,
                                       val repository: ValidateSconRepository,
                                       authAction: GmpAuthAction,
                                       cc: ControllerComponents,
                                       appConfig: AppConfig)(implicit val ec: ExecutionContext)
  extends BackendController(cc) with Logging {

  def validateScon(userId: String): Action[JsValue] = authAction.async(parse.json) { implicit request =>
    withJsonBody[ValidateSconRequest] { validateSconRequest =>
      val redactedScon = redactSensitive(validateSconRequest.scon)
      
      repository.findByScon(validateSconRequest.scon).flatMap {
        case Some(cachedResponse) =>
          logger.debug(s"[ValidateSconController][validateScon] Cache hit for SCON: $redactedScon")
          Future.successful(Ok(Json.toJson(cachedResponse)))

        case None =>
          val validationFuture = if (appConfig.isHipEnabled) {
            logger.debug(s"[ValidateSconController][validateScon] Using HIP connector for SCON: $redactedScon")
            hipConnector.validateScon(userId, validateSconRequest.scon)
              .map { hipResponse =>
                val transformedResult = GmpValidateSconResponse.createFromHipValidateSconResponse(hipResponse)
                logger.debug(s"[ValidateSconController][validateScon] HIP validation successful for SCON: $redactedScon")
                repository.insertByScon(validateSconRequest.scon, transformedResult)
                Ok(Json.toJson(transformedResult))
              }
          } else {
            logger.debug(s"[ValidateSconController][validateScon] Using DES connector for SCON: $redactedScon")
            desConnector.validateScon(userId, validateSconRequest.scon).map { desResponse =>
              val transformedResult = GmpValidateSconResponse.createFromValidateSconResponse(desResponse)
              logger.debug(s"[ValidateSconController][validateScon] DES validation successful for SCON: $redactedScon")
              repository.insertByScon(validateSconRequest.scon, transformedResult)
              Ok(Json.toJson(transformedResult))
            }
          }

          validationFuture.recover {
            case e: IllegalArgumentException =>
              logger.warn(s"[ValidateSconController][validateScon] Invalid SCON format for SCON: $redactedScon")
              BadRequest(Json.obj("error" -> "Invalid SCON format"))

            case e: UpstreamErrorResponse if e.statusCode == 400 =>
              logger.warn(s"[ValidateSconController][validateScon] Bad request for SCON: $redactedScon - ${e.statusCode}")
              BadRequest(Json.obj("error" -> "Invalid request", "details" -> "Bad request"))

            case e: UpstreamErrorResponse if e.statusCode == 500 =>
              logger.error(s"[ValidateSconController][validateScon] Service error for SCON: $redactedScon - ${e.statusCode}")
              InternalServerError(Json.obj("error" -> "Service unavailable"))

            case e: Exception =>
              logger.error(s"[ValidateSconController][validateScon] Unexpected error for SCON: $redactedScon - ${e.getClass.getSimpleName}")
              InternalServerError(Json.obj("error" -> "An unexpected error occurred"))
          }
      }
    }.recover {
      case e: Exception =>
        logger.error(s"[ValidateSconController][validateScon] Request processing failed - ${e.getClass.getSimpleName}")
        BadRequest(Json.obj("error" -> "Invalid request format"))
    }
  }
}
