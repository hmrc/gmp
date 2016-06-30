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

import java.util.UUID
import java.util.concurrent.TimeUnit

import metrics.Metrics
import models.CalculationRequest
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsNumber, JsString, JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.SessionId

import scala.concurrent.Future

class DesConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfter {

  implicit val hc = HeaderCarrier()

  val mockHttp = mock[HttpGet]

  object TestDesConnector extends DesConnector {
    override val http: HttpGet = mockHttp
    override val metrics = new Metrics {
      override def desConnectorTimer(diff: Long, unit: TimeUnit): Unit = {}
    }
  }

  val calcResponseJson = Json.parse(
    """{
           "nino":"AB123456C",
           "rejection_reason":0,
           "npsScon":{
              "contracted_out_prefix":"S",
              "ascn_scon":1301234,
              "modulus_19_suffix":"T"
           },
           "npsLgmpcalc":[
              {
                 "scheme_mem_start_date":"1978-04-06",
                 "scheme_end_date":"200-04-05",
                 "revaluation_rate":1,
                 "gmp_cod_post_eightyeight_tot":1.23,
                 "gmp_cod_allrate_tot":7.88,
                 "gmp_error_code":0,
                "reval_calc_switch_ind": 0
              }
           ]
        }""")

  val validateSconResponseJson = Json.parse(
    """{
          "scon_exists":1
      }"""
  )

  before {
    reset(mockHttp)
  }

  "The Nps Connector" must {

    "calculate" must {

      "return a calculation request" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1234567T", "AB123456C", "Bixby", "Bill", Some(0), None, Some(1), None, None, None))
        val calcResponse = await(result)

        calcResponse.npsLgmpcalc.length must be(1)

      }

      "return an error when 500 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(500,Some(calcResponseJson))))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", None, None, None, None, None, None))
        intercept[Upstream5xxResponse] {
                await(result)
              }
      }

      "return a success when 422 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(422,Some(calcResponseJson))))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None ,None, None, None))
        val calcResponse = await(result)

        calcResponse.rejection_reason must be(0)
      }

      "generate a DES url" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None ,None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must endWith("/scon/S/1401234/Q/nino/CB433298A/surname/SMI/firstname/B/calculation/?calctype=0")
      }

      "use the DES url" in {
        DesConnector.baseURI must be("pensions/individuals/gmp")
      }

      "generate correct url when no revaluation" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None ,None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must endWith("/?calctype=0")
      }

      "truncate surname to 3 chars if length greater than 3 chars" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("/surname/SMI/")
      }

      "not truncate surname if length less than 3 chars" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Fr", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("surname/FR/")
      }

      "remove any whitespace from names" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "LE BON", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("surname/LEB/")

      }

      "generate correct url when names contains special char" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "O'Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("/surname/O%27S/")
      }

      "generate correct url when nino is not all uppercase" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "cb433298a", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("CB433298A")
      }

      "generate correct url when scon is not all uppercase" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("S/1401234/Q")
      }

      "generate correct url with revalrate" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("revalrate")
      }

      "generate correct url with contribution and earnings" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), Some(1), None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("request_earnings")
      }

      "catch calculate audit failure and continue" in {
        val mockAuditConnector = mock[AuditConnector]

        object TestDesConnector extends DesConnector {
          override val auditConnector = mockAuditConnector
          override val http: HttpGet = mockHttp
          override val metrics = new Metrics {
            override def desConnectorTimer(diff: Long, unit: TimeUnit): Unit = {}
          }
        }

        when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(Future.failed(new Exception()))
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), None, None, None))

      }
    }

    "validateScon" must {
      "return a success" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200,Some(validateSconResponseJson))))

        val result = TestDesConnector.validateScon("PSAID","S1401234Q")
        val validateSconResponse = await(result)
        validateSconResponse.scon_exists must be(1)
      }

      "return an error when 500 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(500,Some(validateSconResponseJson))))

        val result = TestDesConnector.validateScon("PSAID","S1401234Q")
        intercept[Upstream5xxResponse] {
                await(result)
              }
      }

      "catch validateScon audit failure and continue" in {
        val mockAuditConnector = mock[AuditConnector]

        object TestNpsConnector extends DesConnector {
          override val auditConnector = mockAuditConnector
          override val http: HttpGet = mockHttp
          override val metrics = new Metrics {
            override def desConnectorTimer(diff: Long, unit: TimeUnit): Unit = {}
          }
        }

        when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(Future.failed(new Exception()))
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200,Some(validateSconResponseJson))))
        val result = TestNpsConnector.validateScon("PSAID","S1401234Q")

      }
    }
  }

  private def successfulCalcHttpResponse(responseJson: Option[JsValue]): JsValue = {
    responseJson.get
  }

}
