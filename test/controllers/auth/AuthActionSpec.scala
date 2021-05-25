/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.util.Timeout
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{OK, UNAUTHORIZED}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers.status
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import scala.concurrent.Future
import scala.language.postfixOps
import scala.concurrent.duration._


class AuthActionSpec extends PlaySpec with GuiceOneAppPerSuite with MockitoSugar{

  class Harness(authAction: GmpAuthAction) extends BaseController {
    def onPageLoad(): Action[AnyContent] = authAction { request => Ok }

    override def controllerComponents: ControllerComponents = stubMessagesControllerComponents()
  }

  val mockMicroserviceAuthConnector = mock[AuthConnector]
  val mockControllerComponents: ControllerComponents = stubMessagesControllerComponents()


  implicit val timeout: Timeout = 5 seconds

  "Auth Action" when {
    "the user is not logged in" must {
      "must return unauthorised" in {

        when(mockMicroserviceAuthConnector.authorise(any(),any())(any(), any()))
          .thenReturn(Future.failed(new MissingBearerToken))

        val authAction = new GmpAuthAction(mockMicroserviceAuthConnector, mockControllerComponents)
        val controller = new Harness(authAction)
        val result = controller.onPageLoad()(FakeRequest("", ""))
        status(result) mustBe UNAUTHORIZED

      }
    }

    "the user is logged in" must {
      "must return the request" in {

        when(mockMicroserviceAuthConnector.authorise[Unit](any(),any())(any(), any()))
          .thenReturn(Future.successful(()))

        val authAction = new GmpAuthAction(mockMicroserviceAuthConnector, mockControllerComponents)
        val controller = new Harness(authAction)

        val result = controller.onPageLoad()(FakeRequest("", ""))
        status(result) mustBe OK

      }
    }
  }
}