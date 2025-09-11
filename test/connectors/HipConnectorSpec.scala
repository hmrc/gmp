/*
 * Copyright 2025 HM Revenue & Customs
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

import config.{AppConfig, Constants}
import metrics.ApplicationMetrics
import models.{HipValidateSconResponse, HipCalculationRequest, EnumRevaluationRate, EnumCalcRequestType}
import models._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalacheck.Gen.const
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class HipConnectorSpec extends HttpClientV2Helper {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockAppConfig: AppConfig = {
    val config = mock[AppConfig]
    when(config.hipUrl).thenReturn("http://localhost:9999")
    when(config.hipAuthorisationToken).thenReturn("dGVzdC1hdXRo") // base64 encoded token
    when(config.originatorIdKey).thenReturn("gov-uk-originator-id")
    when(config.originatorIdValue).thenReturn("HMRC-GMP")
    when(config.hipEnvironmentHeader).thenReturn("Environment" -> "local")
    when(config.isHipEnabled).thenReturn(true)
    config
  }
  val mockMetrics: ApplicationMetrics = mock[ApplicationMetrics]
  
  when(mockAuditConnector.sendEvent(any())(any(), any()))
    .thenReturn(Future.successful(AuditResult.Success))

  // Use the mockHttp and requestBuilder from HttpClientV2Helper
  
  object TestHipConnector extends HipConnector(
    appConfig = mockAppConfig,
    metrics = mockMetrics,
    http = mockHttp,
    auditConnector = mockAuditConnector
  )

  val validateSconResponseJson = Json.parse(
    """{
      |  "schemeContractedOutNumberExists": true
      |}""".stripMargin
  )

  before {
    reset(mockHttp, requestBuilder)
    when(mockHttp.get(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
    when(mockHttp.post(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
    when(requestBuilder.setHeader(any())).thenReturn(requestBuilder)
    when(requestBuilder.withBody(any())(any(), any(), any())).thenReturn(requestBuilder)
    when(requestBuilder.transform(any())).thenReturn(requestBuilder)
  }

  "HipConnector" should {
    "return a valid response for HTTP 200" in {
      implicit val hc = HeaderCarrier()
      requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))

      val result = await(TestHipConnector.validateScon("user123", "S1401234Q"))

      result mustBe HipValidateSconResponse(true)
    }

    "throw UpstreamErrorResponse for error status codes (400, 403, 404, 500, 503)" in {
      val errorCodes = Seq(400, 403, 404, 499, 500, 503)

      errorCodes.foreach { status =>
        implicit val hc = HeaderCarrier()
        requestBuilderExecute(Future.successful(HttpResponse(status, "Error")))

        val exception = intercept[UpstreamErrorResponse] {
          await(TestHipConnector.validateScon("user123", "S1401234Q"))
        }

        exception.statusCode mustBe status
        exception.reportAs mustBe INTERNAL_SERVER_ERROR
      }
    }

    "handle invalid SCON validation" in {
      implicit val hc = HeaderCarrier()

      reset(mockHttp, requestBuilder, mockAuditConnector)

      // Re-stub after reset (safe even if not used)
      when(mockHttp.get(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
      when(requestBuilder.setHeader(any())).thenReturn(requestBuilder)
      when(requestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))

      when(mockAuditConnector.sendEvent(any())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      // Truly invalid formats
      intercept[IllegalArgumentException] {
        await(TestHipConnector.validateScon("user123", "INVALID"))
      }
      intercept[IllegalArgumentException] {
        await(TestHipConnector.validateScon("user123", "S3401234A")) // disallowed 1st digit (3)
      }
      intercept[IllegalArgumentException] {
        await(TestHipConnector.validateScon("user123", "S1401234G")) // disallowed final letter
      }
      intercept[IllegalArgumentException] {
        await(TestHipConnector.validateScon("user123", "S140123A")) // too short
      }

      verify(mockHttp, never()).get(any[URL])(any[HeaderCarrier])
    }
    "accept lowercase/space-insensitive SCON" in {
      implicit val hc = HeaderCarrier()

      when(mockHttp.get(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
      when(requestBuilder.setHeader(any())).thenReturn(requestBuilder)
      when(requestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))
      when(mockAuditConnector.sendEvent(any())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      noException shouldBe thrownBy {
        await(TestHipConnector.validateScon("user123", "s140 1234q")) // → S1401234Q
      }
    }


    "log and continue if audit fails" in {
      implicit val hc = HeaderCarrier()

      when(mockAuditConnector.sendEvent(any())(any(), any()))
        .thenReturn(Future.failed(new RuntimeException("Audit failure")))

      requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))


      // Test that the method completes successfully despite audit failure
      noException shouldBe thrownBy(await(TestHipConnector.validateScon("user123", "S1401234Q")))

    }

    "set required headers including correlationId and originator-id" in {
      implicit val hc = HeaderCarrier(
        requestId = Some(RequestId("test-request-id")),
        sessionId = Some(SessionId("test-session-id"))
      )

      val headersCaptor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])

      requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))
      await(TestHipConnector.validateScon("user123", "S1401234Q"))

      verify(requestBuilder, atLeastOnce()).setHeader(headersCaptor.capture(): _*)
      val captured = headersCaptor.getAllValues.asScala
      val headerMaps = captured.map(_.toMap)

      // Verify correlation ID is present and follows the expected format (UUID)
      val correlationId = headerMaps.flatMap(_.get("correlationId")).headOption
      correlationId must be(defined)
      correlationId.foreach { cid =>
        // UUID regex pattern
        val uuidPattern = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        cid must fullyMatch regex uuidPattern
      }

      // Verify other required headers
      headerMaps.exists(_.get(Constants.OriginatorIdKey).contains(mockAppConfig.originatorIdValue)) mustBe true
      headerMaps.exists(_.get("X-Originating-System").contains(Constants.XOriginatingSystemHeader)) mustBe true
      headerMaps.exists(_.get("X-Transmitting-System").contains(Constants.XTransmittingSystemHeader)) mustBe true
      headerMaps.exists(_.get("Authorization").contains(s"Basic ${mockAppConfig.hipAuthorisationToken}")) mustBe true
    }
  }

  "HipConnector for post call" should {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val calculateUrl: String = "http://localhost:9999/ni/gmp/calculation"
    when(TestHipConnector.calcURI).thenReturn(calculateUrl)

    "return successful response for status 200" in {
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "",
        nationalInsuranceNumber = "S2123456B",
        surname = "TestSurname",
        firstForename = "TestForename",
        secondForename = Some(""),
        revaluationRate = Some(EnumRevaluationRate.NONE),
        calculationRequestType = Some(EnumCalcRequestType.DOL),
        revaluationDate = None,
        terminationDate = None,
        includeContributionAndEarnings = true,
        includeDualCalculation = true
      )
      val successResponse = HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), Some(""), List.empty)
      val httpResponse = HttpResponse(OK, Json.toJson(successResponse).toString())
      requestBuilderExecute(Future.successful(httpResponse))
      await(TestHipConnector.calculate("user123", request)).map { result =>
        result.schemeContractedOutNumberDetails mustBe "S2123456B"
      }
    }

    "throw UpstreamErrorResponse for status 400" in {
      val request = HipCalculationRequest("", "S2123456B", "", "", Some(""),
        Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)
      val errorResponse = UpstreamErrorResponse("Bad Request", BAD_REQUEST, BAD_REQUEST)
      requestBuilderExecute(Future.failed(errorResponse))

      val ex = intercept[UpstreamErrorResponse] {
        await(TestHipConnector.calculate("user123", request))
      }
      ex.statusCode mustBe BAD_REQUEST
      ex.message must include("Bad Request")
    }

    "throw UpstreamErrorResponse for 403 Forbidden" in {
      val request = HipCalculationRequest("S2123456B", "", "", "", Some(""),
        Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)
      val errorResponse = UpstreamErrorResponse("Forbidden", FORBIDDEN, FORBIDDEN)
      requestBuilderExecute(Future.failed(errorResponse))

      val ex = intercept[UpstreamErrorResponse] {
        await(TestHipConnector.calculate("user123", request))
      }
      ex.statusCode mustBe FORBIDDEN
      ex.message must include("Forbidden")
    }

    "throw UpstreamErrorResponse for 404 Not Found Request" in {
      val request = HipCalculationRequest("S2123456B", "", "", "", Some(""),
        Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)
      val errorResponse = UpstreamErrorResponse("Not Found", NOT_FOUND, NOT_FOUND)
      requestBuilderExecute(Future.failed(errorResponse))

      val ex = intercept[UpstreamErrorResponse] {
        await(TestHipConnector.calculate("user123", request))
      }
      ex.statusCode mustBe NOT_FOUND
      ex.message must include("Not Found")
    }

    "fail the future if HTTP call fails" in {
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "",
        surname = "TestSurname",
        firstForename = "TestForename",
        secondForename = Some(""),
        revaluationRate = Some(EnumRevaluationRate.NONE),
        calculationRequestType = Some(EnumCalcRequestType.DOL),
        revaluationDate = None,
        terminationDate = None,
        includeContributionAndEarnings = true,
        includeDualCalculation = true
      )
      requestBuilderExecute(Future.failed(new RuntimeException("Connection error")))
      val ex = intercept[RuntimeException] {
        await(TestHipConnector.calculate("user123", request))
      }
      ex.getMessage must include("Connection error")
    }

    "throw UpstreamErrorResponse for error status code 500" in {
      val request = HipCalculationRequest("", "S2123456B", "", "", Some(""),
        Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)
      val errorResponse = UpstreamErrorResponse("Internal Server Error", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)
      requestBuilderExecute(Future.failed(errorResponse))

      val ex = intercept[UpstreamErrorResponse] {
        await(TestHipConnector.calculate("user123", request))
      }
      ex.statusCode mustBe 500
      ex.reportAs mustBe INTERNAL_SERVER_ERROR
    }

    "throw RuntimeException when JSON validation fails" in {
      val request = HipCalculationRequest("", "S2123456B", "", "", Some(""),
        Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)
      requestBuilderExecute(Future.failed(new RuntimeException("Failed to parse fields")))
      val thrown = intercept[RuntimeException] {
        await(TestHipConnector.calculate("user123", request))
      }
      thrown.getMessage must include("Failed to parse fields")
    }
  }
}
