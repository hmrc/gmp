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

package controllers.auth

import com.google.inject.{Inject, Singleton}
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.Results._
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, ControllerComponents, Request, Result}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, ConfidenceLevel, NoActiveSession}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import scala.concurrent.Future

@Singleton
class GmpAuthAction @Inject()(override val authConnector: AuthConnector, controllerComponents: ControllerComponents)
  extends ActionBuilder[Request, AnyContent] with AuthorisedFunctions {

  implicit val executionContext = controllerComponents.executionContext
  val parser: BodyParser[AnyContent] = controllerComponents.parsers.defaultBodyParser


  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)

    authorised(ConfidenceLevel.L50) {
      block(request)
    }recover {
      case ex: NoActiveSession =>
        Status(UNAUTHORIZED)
    }
  }
}

