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

package connectors

import base.BaseSpec
import java.util.UUID
import metrics.ApplicationMetrics
import models.CalculationRequest
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.ArgumentCaptor
import play.api.Configuration
import play.api.libs.json._
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class DesConnectorSpec extends BaseSpec {

  val returnHeaders = Map("Session" -> Seq("session1"))

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val mockHttp: HttpClient = mock[HttpClient]

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  val mockServicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]
  val config = app.injector.instanceOf[Configuration]

  when(mockAuditConnector.sendEvent(any())(any(), any()))
    .thenReturn(Future.successful(AuditResult.Success))

  object TestDesConnector extends DesConnector(config,
    mock[ApplicationMetrics],
    mockHttp,
    mockAuditConnector,
    mockServicesConfig)

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

  val citizenDetailsJson = Json.parse(
    """{
                  "etag" : "115"
                }
            """.stripMargin)

  before {
    reset(mockHttp)
  }

  "The Nps Connector" must {

    "calculate" must {

      "return a calculation request" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1234567T", "AB123456C", "Bixby", "Bill", Some(0), None, Some(1), None, None, None))
        val calcResponse = await(result)

        calcResponse.npsLgmpcalc.length must be(1)

      }

      "return an error when 500 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(500, calcResponseJson, returnHeaders)))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", None, None, None, None, None, None))
        intercept[UpstreamErrorResponse] {
          await(result)
        }
      }

      "return a success when 422 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(422, calcResponseJson, returnHeaders)))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))
        val calcResponse = await(result)

        calcResponse.rejection_reason must be(0)
      }

      "return a response when 400 returned" in {
        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(400, "400")))

        val result = TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))
        val calcResponse = await(result)

        calcResponse.rejection_reason must be(400)
      }

      "generate a DES url" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must endWith("/scon/S/1401234/Q/nino/CB433298A/surname/SMI/firstname/B/calculation/?calctype=0")
      }

      "use the DES url" in {
        new DesConnector(app.configuration,
          mock[ApplicationMetrics],
          mockHttp,
          mock[AuditConnector],
          mockServicesConfig).baseURI must be("pensions/individuals/gmp")
      }

      "generate correct url when no revaluation" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must endWith("/?calctype=0")
      }

      "truncate surname to 3 chars if length greater than 3 chars" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must include("/surname/SMI/")
      }

      "not truncate surname if length less than 3 chars" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "Fr", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must include("surname/FR/")
      }

      "don't remove whitespace from names" in {
        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "LE BON", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must include("surname/LE+/")

      }

      "generate correct url when names contains special char" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "CB433298A", "O'Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must include("/surname/O%27S/")
      }

      "generate correct url when nino is not all uppercase" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("S1401234Q", "cb433298a", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must include("CB433298A")
      }

      "generate correct url when scon is not all uppercase" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(0), None, None, None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must include("S/1401234/Q")
      }

      "generate correct url with revalrate" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), None, None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must include("revalrate")
      }

      "generate correct url with contribution and earnings" in {

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any())).thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), Some(1), None, None))

        verify(mockHttp).GET[HttpResponse](urlCaptor.capture(), any(), any())(any(), any(), any())

        urlCaptor.getValue must include("request_earnings")
      }

      "catch calculate audit failure and continue" in {

        object TestDesConnector extends DesConnector(app.configuration,
          mock[ApplicationMetrics],
          mockHttp,
          mockAuditConnector,
          mockServicesConfig)

        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.failed(new Exception()))
        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(200, calcResponseJson, returnHeaders)))
        TestDesConnector.calculate("PSAID", CalculationRequest("s1401234q", "cb433298a", "Smith", "Bill", Some(1), None, Some(1), None, None, None))
        //TODO: Assert something here?
      }
    }

    "validateScon" must {
      "return a success" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(200, validateSconResponseJson, returnHeaders)))

        val result = TestDesConnector.validateScon("PSAID", "S1401234Q")
        val validateSconResponse = await(result)
        validateSconResponse.scon_exists must be(1)
      }

      "return an error when 500 returned" in {

        implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(500, validateSconResponseJson, returnHeaders)))

        val result = TestDesConnector.validateScon("PSAID", "S1401234Q")
        intercept[UpstreamErrorResponse] {
          await(result)
        }
      }

      "catch validateScon audit failure and continue" in {

        object TestNpsConnector extends DesConnector(app.configuration,
          mock[ApplicationMetrics],
          mockHttp,
          mockAuditConnector,
          mockServicesConfig)

        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.failed(new Exception()))
        when(mockHttp.GET[HttpResponse](any(), any(), any())
          (any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(200, validateSconResponseJson, returnHeaders)))

        TestNpsConnector.validateScon("PSAID", "S1401234Q")
        //TODO: Assert something here?
      }
    }

    "Calling getPersonDetails" should {

      val nino = "AB123456C"

      "return a DesHiddenRecordResponse when manualCorrespondenceInd=true" in {

        val response = HttpResponse(423, citizenDetailsJson, returnHeaders)

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())) thenReturn {
          Future.successful(response)
        }

        val pd = TestDesConnector.getPersonDetails(nino)
        await(pd) must be(DesGetHiddenRecordResponse)
      }

      "return DesGetSuccessResponse when manualCorrespondenceInd=false" in {

        val response = HttpResponse(200, citizenDetailsJson, returnHeaders)
        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())) thenReturn {
          Future.successful(response)
        }

        val pd = TestDesConnector.getPersonDetails(nino)
        await(pd) must be(DesGetSuccessResponse)
      }

      "return a DesNotFoundResponse when HOD returns 404" in {

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())) thenReturn {
          Future.failed(UpstreamErrorResponse("Not found", 404))
        }

        val pd = TestDesConnector.getPersonDetails(nino)
        await(pd) must be(DesGetNotFoundResponse)
      }

      "return a DesErrorResponse if any other issues" in {
        val ex = new Exception("Exception")
        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())) thenReturn {
          Future.failed(ex)
        }

        val pd = TestDesConnector.getPersonDetails(nino)
        await(pd) must be(DesGetErrorResponse(ex))

      }

      "return a success response if the MCI flag does not appear in the response" in {
        val json = Json.parse("{}")
        val response = HttpResponse(200, json, returnHeaders)

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())) thenReturn Future.successful(response)

        val result = TestDesConnector.getPersonDetails(nino)
        await(result) must be(DesGetSuccessResponse)
      }

      "return an Unexpected Response with Internal Server response or DES is down" in {
        val response = HttpResponse.apply(500, citizenDetailsJson, returnHeaders)

        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any())) thenReturn Future.successful(response)

        val result = TestDesConnector.getPersonDetails(nino)
        await(result) must be(DesGetUnexpectedResponse)
      }

    }

  }
}