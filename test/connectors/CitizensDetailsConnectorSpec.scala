/*
 * Copyright 2016 HM Revenue & Customs
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

package connectors

import java.util.concurrent.TimeUnit

import metrics.Metrics
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import uk.gov.hmrc.play.http.{HttpResponse, HttpGet, HeaderCarrier}
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.Future

class CitizensDetailsConnectorSpec extends PlaySpec with OneAppPerSuite with MockitoSugar with BeforeAndAfter{


  implicit val hc = HeaderCarrier()

  val mockHttp = mock[HttpGet]

  object TestConnector extends CitizensDetailsConnector {
    override val http: HttpGet = mockHttp
  }

  "CitizensDetailsConnector" must {

    "return OK when nino is ok" in{

      val nino = "AB123456C"

      when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200)))

      val result = await(TestConnector.getDesignatoryDetails(nino))

      result must be(OK)

    }

  }
}
