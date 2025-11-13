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
import models.{EnumCalcRequestType, EnumRevaluationRate, HipCalculationRequest, HipValidateSconResponse}
import models.*
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import play.api.libs.json.{JsResultException, JsString, Json}
import HipCalcResult.{Failures, Success}
import play.api.test.Helpers.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.*
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}

import java.net.URL
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

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

    "return Failures(HipCalculationFailuresResponse) for status 500 when failures only contain type" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
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

      val failuresJson =
        """{
           "origin": "HIP",
           "response": {
             "failures": [
               { "type": "SomeFailureType", "reason": "Integration unavailable" }
             ]
           }
         }"""

      requestBuilderExecute(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, failuresJson)))

      val res = await(TestHipConnector.calculate("user123", request))
      res match {
        case Failures(value, status) =>
          status mustBe INTERNAL_SERVER_ERROR
          value.failures must have size 1
          value.failures.head.code mustBe None
          value.failures.head.failureType mustBe Some("SomeFailureType")
          value.failures.head.reason mustBe "Integration unavailable"
          value.origin mustBe Some("HIP")
        case _ => fail("Expected Failures")
      }
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


  "return JsResultException when 200 but body is not HipValidateSconResponse" in {
    implicit val hc = HeaderCarrier()
    // 200 with an unexpected shape
    requestBuilderExecute(Future.successful(HttpResponse(OK, """{"unexpected":"field"}""")))
    intercept[JsResultException] {
      await(TestHipConnector.validateScon("user123", "S1401234Q"))
    }
  }

  "propagate transport failure (Future.failed) from http.get" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.failed(new RuntimeException("boom")))
    val ex = intercept[RuntimeException] {
      await(TestHipConnector.validateScon("user123", "S1401234Q"))
    }
    ex.getMessage must include("boom")
  }

  "map HTTP 400 response to UpstreamErrorResponse (reportAs=400)" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(BAD_REQUEST, "Bad req body")))
    val ex = intercept[UpstreamErrorResponse] {
      await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)))
    }
    ex.statusCode mustBe BAD_REQUEST
    ex.reportAs mustBe BAD_REQUEST
    ex.message must include("HIP returned non-JSON body with 400. Body: Bad req body")
  }

  "map HTTP 403 response to UpstreamErrorResponse (reportAs=403)" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(FORBIDDEN, "Forbidden body")))
    val ex = intercept[UpstreamErrorResponse] {
      await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)))
    }
    ex.statusCode mustBe FORBIDDEN
    ex.reportAs mustBe FORBIDDEN
  }

  "map HTTP 404 response to UpstreamErrorResponse (reportAs=404)" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(NOT_FOUND, "not found")))
    val ex = intercept[UpstreamErrorResponse] {
      await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)))
    }
    ex.statusCode mustBe NOT_FOUND
    ex.reportAs mustBe NOT_FOUND
  }

  "map HTTP 500 response to UpstreamErrorResponse (reportAs=500)" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "ise")))
    val ex = intercept[UpstreamErrorResponse] {
      await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)))
    }
    ex.statusCode mustBe INTERNAL_SERVER_ERROR
    ex.reportAs mustBe INTERNAL_SERVER_ERROR
  }

  "map unexpected HTTP 429 to UpstreamErrorResponse (reportAs=429)" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(429, "Too Many Requests")))
    val ex = intercept[UpstreamErrorResponse] {
      await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)))
    }
    ex.statusCode mustBe 429
    ex.reportAs mustBe 429
  }

  "throw RuntimeException with detailed message when 200 but JSON cannot be parsed" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(OK, """{"unexpected":"value"}""")))
    val ex = intercept[UpstreamErrorResponse] {
      await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.DOL), None, None, true, true)))
    }
    ex.getMessage must include("HIP returned invalid JSON")
    ex.reportAs mustBe BAD_GATEWAY
  }
  "complete successfully even if audit sendEvent fails" in {
    implicit val hc = HeaderCarrier()
    when(mockAuditConnector.sendEvent(any())(any(), any()))
      .thenReturn(Future.failed(new RuntimeException("audit down")))
    val successBody = Json.toJson(HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), List.empty)).toString()
    requestBuilderExecute(Future.successful(HttpResponse(OK, successBody)))

    val result = await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.DOL), None, None, true, true)))
    result match {
      case Success(value) => value.schemeContractedOutNumberDetails mustBe "S2123456B"
      case _ => fail("Expected Success")
    }
  }
  "set required headers on calculate (correlationId + auth + originator)" in {
    implicit val hc = HeaderCarrier(requestId = Some(RequestId("rid")), sessionId = Some(SessionId("sid")))
    val headerCaptor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])

    val successBody = Json.toJson(HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), List.empty)).toString()
    requestBuilderExecute(Future.successful(HttpResponse(OK, successBody)))
    await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.DOL), None, None, true, true)))

    verify(requestBuilder, atLeastOnce()).setHeader(headerCaptor.capture(): _*)
    val headerMaps = headerCaptor.getAllValues.asScala.map(_.toMap)

    val corr = headerMaps.flatMap(_.get("correlationId")).headOption
    corr must be(defined)
    corr.get must fullyMatch regex "[0-9a-fA-F\\-]{36}"

    headerMaps.exists(_.get(Constants.OriginatorIdKey).contains(mockAppConfig.originatorIdValue)) mustBe true
    headerMaps.exists(_.get("Authorization").exists(_.startsWith("Basic "))) mustBe true
    headerMaps.exists(_.contains("X-Originating-System")) mustBe true
    headerMaps.exists(_.contains("X-Transmitting-System")) mustBe true
    headerMaps.exists(_.contains("X-Receipt-Date")) mustBe true
  }
  "record metrics on success" in {
    implicit val hc = HeaderCarrier()
    val body = Json.toJson(HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), List.empty)).toString()
    requestBuilderExecute(Future.successful(HttpResponse(OK, body)))

    await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.DOL), None, None, true, true)))

    verify(mockMetrics, atLeastOnce()).hipConnectorStatus(OK)
    verify(mockMetrics, atLeastOnce())
      .hipConnectorTimer(ArgumentMatchers.anyLong(), ArgumentMatchers.eq(TimeUnit.MILLISECONDS))
  }

  // ---------- calculate: 200 but NON-JSON body ----------
  "return RuntimeException when 200 but non-JSON body" in {
    implicit val hc = HeaderCarrier()
    // Body is plain text, not JSON
    requestBuilderExecute(Future.successful(HttpResponse(OK, "not-json at all")))
    val ex = intercept[UpstreamErrorResponse] {
      await(
        TestHipConnector.calculate(
          "user123",
          HipCalculationRequest("", "S2123456B", "s", "f", Some(""),
            Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.DOL), None, None, true, true)
        )
      )
    }
    ex.getMessage must include("non-JSON body with 200")
    ex.reportAs mustBe BAD_GATEWAY
  }

  // ---------- calculate: ensure headers are propagated in UpstreamErrorResponse ----------
  "propagate response headers in error (calculate)" in {
    implicit val hc = HeaderCarrier()
    val hdrs = Map("correlationId" -> Seq("cid-123"))
    requestBuilderExecute(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "ise", headers = hdrs)))

    val ex = intercept[UpstreamErrorResponse] {
      await(
        TestHipConnector.calculate(
          "user123",
          HipCalculationRequest("", "S2123456B", "s", "f", Some(""),
            Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)
        )
      )
    }
    ex.statusCode mustBe INTERNAL_SERVER_ERROR
    ex.headers.get("correlationId").flatMap(_.headOption) mustBe Some("cid-123")
  }

  // ---------- calculate: 429 should use “Client error (Status: 429)” message ----------
  "map HTTP 429 to UpstreamErrorResponse with client-error message" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(429, "Too Many Requests")))
    val ex = intercept[UpstreamErrorResponse] {
      await(
        TestHipConnector.calculate(
          "user123",
          HipCalculationRequest("", "S2123456B", "s", "f", Some(""),
            Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None, None, true, true)
        )
      )
    }
    ex.statusCode mustBe 429
    ex.reportAs mustBe 429
    ex.getMessage must include("HIP connector calculate failed with status 429")
  }

  // ---------- validateScon: unexpected status (e.g., 418) uses “unexpected response” path ----------
  "validateScon returns UpstreamErrorResponse for unexpected status (418)" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(418, "teapot")))
    val ex = intercept[UpstreamErrorResponse] {
      await(TestHipConnector.validateScon("user123", "S1401234Q"))
    }
    ex.statusCode mustBe 418
    ex.reportAs mustBe INTERNAL_SERVER_ERROR
    ex.getMessage must include("unexpected response")
  }

  // ---------- validateScon: metrics recorded on non-200 ----------
  "validateScon records metrics on HTTP error" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(NOT_FOUND, "nope")))
    intercept[UpstreamErrorResponse] {
      await(TestHipConnector.validateScon("user123", "S1401234Q"))
    }
    verify(mockMetrics, atLeastOnce()).hipConnectorStatus(NOT_FOUND)
  }


  "record metrics on HTTP error" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "ise")))
    intercept[UpstreamErrorResponse] {
      await(TestHipConnector.calculate("user123", HipCalculationRequest("", "S2123456B", "s", "f", Some(""), Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.DOL), None, None, true, true)))
    }
    verify(mockMetrics, atLeastOnce()).hipConnectorStatus(INTERNAL_SERVER_ERROR)
  }
  "normalizeScon strips spaces and uppercases valid SCON" in {
    implicit val hc = HeaderCarrier()
    requestBuilderExecute(Future.successful(HttpResponse(OK, """{"schemeContractedOutNumberExists":true}""")))
    noException shouldBe thrownBy {
      await(TestHipConnector.validateScon("user123", "s140 1234q"))
    }
  }

  "normalizeScon rejects disallowed final letters and patterns" in {
    implicit val hc = HeaderCarrier()
    intercept[IllegalArgumentException] {
      await(TestHipConnector.validateScon("user123", "S1401234G")) // G not allowed by regex
    }
    intercept[IllegalArgumentException] {
      await(TestHipConnector.validateScon("user123", "S3401234A")) // '3' not in allowed set
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
      val successResponse = HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), List.empty)
      val httpResponse = HttpResponse(OK, Json.toJson(successResponse).toString())
      requestBuilderExecute(Future.successful(httpResponse))
      val res = await(TestHipConnector.calculate("user123", request))
      res match {
        case Success(value) => value.schemeContractedOutNumberDetails mustBe "S2123456B"
        case _ => fail("Expected Success")
      }
    }

    "serialize LocalDate fields as yyyy-MM-dd in request body" in {
      val revaluationDateStr = "2022-06-01"
      val terminationDateStr = "2022-06-01"

      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
        surname = "TestSurname",
        firstForename = "TestForename",
        secondForename = Some(""),
        revaluationRate = Some(EnumRevaluationRate.NONE),
        calculationRequestType = Some(EnumCalcRequestType.DOL),
        revaluationDate = Some(java.time.LocalDate.parse(revaluationDateStr)),
        terminationDate = Some(java.time.LocalDate.parse(terminationDateStr)),
        includeContributionAndEarnings = true,
        includeDualCalculation = true
      )

      val successResponse = HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), List.empty)
      val httpResponse = HttpResponse(OK, Json.toJson(successResponse).toString())
      requestBuilderExecute(Future.successful(httpResponse))

      await(TestHipConnector.calculate("user123", request))

      val bodyCaptor = org.mockito.ArgumentCaptor.forClass(classOf[play.api.libs.json.JsValue])
      verify(requestBuilder, atLeastOnce()).withBody(bodyCaptor.capture())(any(), any(), any())
      val sentJson = bodyCaptor.getValue

      (sentJson \ "revaluationDate").as[JsString].value mustBe revaluationDateStr
      (sentJson \ "terminationDate").as[JsString].value mustBe terminationDateStr
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

    "return Failures(HipCalculationFailuresResponse) for status 422 with failures body" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
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

      val failuresJson =
        """{
           "failures": [ { "reason": "No match for person details provided", "code": "63119" } ]
         }"""

      requestBuilderExecute(Future.successful(HttpResponse(UNPROCESSABLE_ENTITY, failuresJson)))

      val res = await(TestHipConnector.calculate("user123", request))
      res match {
        case Failures(value, status) =>
          status mustBe UNPROCESSABLE_ENTITY
          value.failures.head.reason mustBe "No match for person details provided"
          value.failures.head.code mustBe Some("63119")
          value.failures.head.failureType mustBe None
          value.origin mustBe None
        case _ => fail("Expected Failures")
      }
    }

    "return Failures(HipCalculationFailuresResponse) for status 400 with nested failures body" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
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

      val failuresJson =
        """{
           "origin": "HoD",
           "response": {
             "failures": [
               { "reason": "HTTP message not readable", "code": "400.2" },
               { "reason": "Constraint Violation - Invalid/Missing input parameter", "code": "400.1" }
             ]
           }
         }"""

      requestBuilderExecute(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, failuresJson)))

      val res = await(TestHipConnector.calculate("user123", request))
      res match {
        case Failures(value, status) =>
          status mustBe SERVICE_UNAVAILABLE
          value.failures.flatMap(_.code) mustBe List("400.2", "400.1")
          value.failures.map(_.reason) must contain("HTTP message not readable")
          value.failures.flatMap(_.failureType) mustBe empty
          value.origin mustBe Some("HoD")
        case _ => fail("Expected Failures")
      }
    }

    "return Failures(HipCalculationFailuresResponse) for status 403 with single failure body" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
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

      val failuresJson =
        """{
           "reason": "User Not Authorised",
           "code": "403.1"
         }"""

      requestBuilderExecute(Future.successful(HttpResponse(FORBIDDEN, failuresJson)))

      val res = await(TestHipConnector.calculate("user123", request))
      res match {
        case Failures(value, status) =>
          status mustBe FORBIDDEN
          value.failures must have size 1
          value.failures.head.reason mustBe "User Not Authorised"
          value.failures.head.code mustBe Some("403.1")
          value.failures.head.failureType mustBe None
          value.origin mustBe None
        case _ => fail("Expected Failures")
      }
    }

    "return Failures(HipCalculationFailuresResponse) when failures only contain type" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
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

      val failuresJson =
        """{
           "origin": "HIP",
           "response": {
             "failures": [
               { "type": "SomeFailureType", "reason": "Integration unavailable" }
             ]
           }
         }"""

      requestBuilderExecute(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, failuresJson)))

      val res = await(TestHipConnector.calculate("user123", request))
      res match {
        case Failures(value, status) =>
          status mustBe SERVICE_UNAVAILABLE
          value.failures must have size 1
          value.failures.head.code mustBe None
          value.failures.head.failureType mustBe Some("SomeFailureType")
          value.failures.head.reason mustBe "Integration unavailable"
          value.origin mustBe Some("HIP")
        case _ => fail("Expected Failures")
      }
    }

    "return Left with code=0 when 422 has non-numeric code" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
        surname = "TestSurname",
        firstForename = "TestForename",
        secondForename = Some("")
        , revaluationRate = Some(EnumRevaluationRate.NONE)
        , calculationRequestType = Some(EnumCalcRequestType.DOL)
        , revaluationDate = None
        , terminationDate = None
        , includeContributionAndEarnings = true
        , includeDualCalculation = true
      )

      val failuresJson =
        """{
           "failures": [ { "reason": "Some reason", "code": "ABC" } ]
         }"""

      requestBuilderExecute(Future.successful(HttpResponse(UNPROCESSABLE_ENTITY, failuresJson)))

      val res = await(TestHipConnector.calculate("user123", request))
      res match {
        case Failures(value, status) =>
          status mustBe UNPROCESSABLE_ENTITY
          value.failures.head.code mustBe Some("0")
          value.failures.head.failureType mustBe None
        case _ => fail("Expected Failures")
      }
    }

    "throw RuntimeException when 422 but non-JSON body" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
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
      requestBuilderExecute(Future.successful(HttpResponse(UNPROCESSABLE_ENTITY, "not-json")))
      val ex = intercept[UpstreamErrorResponse] {
        await(TestHipConnector.calculate("user123", request))
      }
      ex.getMessage must include("HIP returned non-JSON body with 422")
      ex.reportAs mustBe BAD_GATEWAY
    }

    "throw RuntimeException when 422 body has unexpected shape" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val request = HipCalculationRequest(
        schemeContractedOutNumber = "S2123456B",
        nationalInsuranceNumber = "AA000001A",
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
      requestBuilderExecute(Future.successful(HttpResponse(UNPROCESSABLE_ENTITY, """{"foo":"bar"}""")))
      val ex = intercept[UpstreamErrorResponse] {
        await(TestHipConnector.calculate("user123", request))
      }
      ex.getMessage must include("HIP 422 returned invalid JSON")
      ex.reportAs mustBe BAD_GATEWAY
    }
  }
}
