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

import config.ApplicationConfig
import metrics.Metrics
import models.CalculationRequest
import org.mockito.Matchers._
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, OneServerPerSuite, PlaySpec}
import play.api.libs.json._
import play.api.test.Helpers._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.SessionId

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class DesConnectorSpec extends PlaySpec with OneAppPerSuite with MockitoSugar with BeforeAndAfter with ApplicationConfig {

  implicit val hc = HeaderCarrier()

  val mockHttp = mock[HttpGet]

  object TestDesConnector extends DesConnector {
    override val http: HttpGet = mockHttp
    override val metrics = mock[Metrics]
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

  val desResponse = Json.parse(
    """{
        "brailleOutputRequired" : false,
        "deceased" : false,
        "dateOfBirth" : "1969-03-05",
        "manualCorrespondenceInd" : false,
        "hasSelfAssessmentAccount" : false,
        "dateOfRegistration" : "2000-01-01",
        "ninoSuffix" : "C",
        "phoneNumber" : {
          "1" : {
          "telephoneNumber" : "01999123456",
          "telephoneType" : 1
        }
        },
        "registrationType" : 0,
        "names" : {
          "1" : {
          "sequenceNumber" : 12345,
          "firstForenameOrInitial" : "Angus",
          "secondForenameOrInitial" : "John",
          "startDate" : "2000-01-01",
          "surname" : "Smith",
          "title" : 1,
          "honours" : null
        }
        },
        "nino" : "AB123456",
        "audioOutputRequired" : false,
        "welshOutputRequired" : false,
        "sex" : "M",
        "dateOfEntry" : "2000-01-01",
        "addresses" : {
          "1" : {
          "sequenceNumber" : 1,
          "line1" : 123456,
          "line2" : 23456,
          "line3" : 3456,
          "line4" : 456,
          "postcode" : 98765,
          "startDate" : "2000-01-01"
        },
          "2" : {
          "sequenceNumber" : 1,
          "line1" : "1 Main Street",
          "line2" : "Central",
          "line3" : "Anothertown",
          "line4" : "Anothershire",
          "postcode" : "NE27 5FG",
          "startDate" : "2012-07-01"
        }
        },
        "largePrintOutputRequired" : false,
        "accountStatus" : 0
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
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1234567T", "AB123456C", "Bixby", "Bill", Some(0), None, Some(1), None, None, None))
        val calcResponse = await(result)

        calcResponse.npsLgmpcalc.length must be(1)

      }

      "return an error when 500 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(500, Some(calcResponseJson))))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", None, None, None, None, None, None))
        intercept[Upstream5xxResponse] {
          await(result)
        }
      }

      "return a success when 422 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(422, Some(calcResponseJson))))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))
        val calcResponse = await(result)

        calcResponse.rejection_reason must be(0)
      }

      "return a response when 400 returned" in {
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(400, None)))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))
        val calcResponse = await(result)

        calcResponse.rejection_reason must be(400)
      }

      "generate a DES url" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must endWith("/scon/S/1401234/Q/nino/CB433298A/surname/SMI/firstname/B/calculation/?calctype=0")
      }

      "use the DES url" in {
        DesConnector.baseURI must be("pensions/individuals/gmp")
      }

      "generate correct url when no revaluation" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must endWith("/?calctype=0")
      }

      "truncate surname to 3 chars if length greater than 3 chars" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("/surname/SMI/")
      }

      "not truncate surname if length less than 3 chars" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Fr", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("surname/FR/")
      }

      "remove any whitespace from names" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "LE BON", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("surname/LEB/")

      }

      "generate correct url when names contains special char" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "O'Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("/surname/O%27S/")
      }

      "generate correct url when nino is not all uppercase" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "cb433298a", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("CB433298A")
      }

      "generate correct url when scon is not all uppercase" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("S/1401234/Q")
      }

      "generate correct url with revalrate" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("revalrate")
      }

      "generate correct url with contribution and earnings" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), Some(1), None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any())

        urlCaptor.getValue must include("request_earnings")
      }

      "catch calculate audit failure and continue" in {
        val mockAuditConnector = mock[AuditConnector]

        object TestDesConnector extends DesConnector {
          override val auditConnector = mockAuditConnector
          override val http: HttpGet = mockHttp
          override val metrics = mock[Metrics]
        }

        when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(Future.failed(new Exception()))
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(calcResponseJson))))
        val result = TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), None, None, None))

      }
    }

    "validateScon" must {
      "return a success" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(validateSconResponseJson))))

        val result = TestDesConnector.validateScon("PSAID", "S1401234Q")
        val validateSconResponse = await(result)
        validateSconResponse.scon_exists must be(1)
      }

      "return an error when 500 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(500, Some(validateSconResponseJson))))

        val result = TestDesConnector.validateScon("PSAID", "S1401234Q")
        intercept[Upstream5xxResponse] {
          await(result)
        }
      }

      "catch validateScon audit failure and continue" in {
        val mockAuditConnector = mock[AuditConnector]

        object TestNpsConnector extends DesConnector {
          override val auditConnector = mockAuditConnector
          override val http: HttpGet = mockHttp
          override val metrics = mock[Metrics]
        }

        when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(Future.failed(new Exception()))
        when(mockHttp.GET[HttpResponse](Matchers.any())
          (Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(200, Some(validateSconResponseJson))))

        TestNpsConnector.validateScon("PSAID", "S1401234Q")

      }
    }

    "Calling getPersonDetails" should {

      val nino = "AB123456C"

      "return a DesHiddenRecordResponse when manualCorrespondenceInd=true" in {
        val badPerson = desResponse.as[JsObject] + ("manualCorrespondenceInd" -> JsBoolean(true))
        val r = HttpResponse(200, Some(badPerson), Map("ETag" -> Seq("115")))

        when(mockHttp.GET[HttpResponse](Matchers.any())(any(), any())) thenReturn {
          Future.successful(r)
        }

        val pd = TestDesConnector.getPersonDetails(nino)

        await(pd) must be(DesGetHiddenRecordResponse)

        verify(TestDesConnector.metrics, times(1)).mciLockCount()
      }

      "return DesGetSuccessResponse when manualCorrespondenceInd=false" in {

        val r = HttpResponse(200, Some(desResponse.as[JsObject]), Map("ETag" -> Seq("115")))
        when(mockHttp.GET[HttpResponse](Matchers.any())(any(), any())) thenReturn {
          Future.successful(r)
        }

        val pd = TestDesConnector.getPersonDetails("AB123456C")

        await(pd) must be(DesGetSuccessResponse)
      }

      "return a DesNotFoundResponse when HOD returns 404" in {

        when(mockHttp.GET[HttpResponse](Matchers.any())(any(), any())) thenReturn {
          Future.failed(new NotFoundException("Not found"))
        }

        val pd = TestDesConnector.getPersonDetails("AB123456C")

        await(pd) must be(DesGetNotFoundResponse)
      }

      "return a DesErrorResponse if any other issues" in {
        val ex = new Exception("Exception")
        val r = HttpResponse(200, Some(desResponse.as[JsObject]), Map("ETag" -> Seq("115")))
        when(mockHttp.GET[HttpResponse](Matchers.any())(any(), any())) thenReturn {
          Future.failed(ex)
        }

        val pd = TestDesConnector.getPersonDetails("AB123456C")

        await(pd) must be(DesGetErrorResponse(ex))
      }

      "handles a response that does not include the MCI flag" in {

        val json = Json.parse("{}")

        val response = HttpResponse(200, Some(json), Map("ETag" -> Seq("115")))

        when(mockHttp.GET[HttpResponse](anyString)(any(), any[HeaderCarrier])) thenReturn {
          Future.successful(response)
        }

        val pd = TestDesConnector.getPersonDetails(nino)

        await(pd) must be(DesGetSuccessResponse)
      }
    }
  }

  private def successfulCalcHttpResponse(responseJson: Option[JsValue]): JsValue = {
    responseJson.get
  }


}
