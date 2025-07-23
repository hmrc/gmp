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
import connectors.{DesConnector, HipConnector, IFConnector}
import controllers.auth.GmpAuthAction
import models.{GmpValidateSconResponse, ValidateSconRequest}
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import repositories.ValidateSconRepository
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ValidateSconController @Inject()(desConnector: DesConnector,
                                       ifConnector: IFConnector,
                                       hipConnector: HipConnector,
                                       val repository: ValidateSconRepository,
                                       authAction: GmpAuthAction,
                                       cc: ControllerComponents,
                                       appConfig: AppConfig) (implicit val ec: ExecutionContext) extends BackendController(cc) with Logging {

  def validateScon(userId: String): Action[JsValue] = authAction.async(parse.json) { implicit request =>
    withJsonBody[ValidateSconRequest] { validateSconRequest =>
      repository.findByScon(validateSconRequest.scon).flatMap {
        case Some(cachedResponse) =>
          Future.successful(Ok(Json.toJson(cachedResponse)))

        case None =>
          val validationFuture = if (appConfig.isHipEnabled) {
            hipConnector.validateScon(userId, validateSconRequest.scon).map { hipResponse =>
              val transformedResult = GmpValidateSconResponse.createFromHipValidateSconResponse(hipResponse)
              logger.debug(s"[ValidateSconController][validateScon][transformed HIP Result]: $transformedResult")
              repository.insertByScon(validateSconRequest.scon, transformedResult)
              Ok(Json.toJson(transformedResult))
            }
          } else {
            desConnector.validateScon(userId, validateSconRequest.scon).map { desResponse =>
              val transformedResult = GmpValidateSconResponse.createFromValidateSconResponse(desResponse)
              logger.debug(s"[ValidateSconController][validateScon][transformed DES Result]: $transformedResult")
              repository.insertByScon(validateSconRequest.scon, transformedResult)
              Ok(Json.toJson(transformedResult))
            }
          }

          validationFuture.recoverWith {
            case e: UpstreamErrorResponse if e.statusCode == 500 =>
              logger.debug(s"[ValidateSconController][validateScon][ERROR:500]: ${e.getMessage}")
              Future.successful(InternalServerError(e.getMessage))
          }

      }
    }
  }

}
