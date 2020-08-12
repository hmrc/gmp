/*
 * Copyright 2020 HM Revenue & Customs
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
import connectors.DesConnector
import controllers.auth.GmpAuthAction
import models.{GmpValidateSconResponse, ValidateSconRequest}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import repositories.ValidateSconRepository
import uk.gov.hmrc.http.Upstream5xxResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ValidateSconController @Inject()(desConnector: DesConnector,
                                       val repository: ValidateSconRepository,
                                       authAction: GmpAuthAction,
                                       cc: ControllerComponents) (implicit val ec: ExecutionContext) extends BackendController(cc) {

  def validateScon(userId: String): Action[JsValue] = authAction.async(parse.json) {

    implicit request => {

      withJsonBody[ValidateSconRequest] { validateSconRequest =>

        repository.findByScon(validateSconRequest.scon).flatMap {
          case Some(cr) => Future.successful(Ok(Json.toJson(cr)))
          case None => {
            val result = desConnector.validateScon(userId, validateSconRequest.scon)

            result.map {
              validateResult => {
                Logger.debug(s"[ValidateSconController][validateScon] : $validateResult")
                val transformedResult = GmpValidateSconResponse.createFromValidateSconResponse(validateResult)

                Logger.debug(s"[ValidateSconController][transformedResult] : $transformedResult")
                repository.insertByScon(validateSconRequest.scon, transformedResult)
                Ok(Json.toJson(transformedResult))
              }
            }.recover {
              case e: Upstream5xxResponse if e.upstreamResponseCode == 500 => {
                Logger.debug(s"[ValidateSconController][validateScon][transformedResult][ERROR:500] : ${e.getMessage}")
                InternalServerError(e.getMessage)
              }
            }
          }
        }
      }
    }
  }

}