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
import models.HipValidateSconResponse
import models._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalacheck.Gen.const
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class HipConnectorSpec extends HttpClientV2Helper {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockMetrics: ApplicationMetrics = mock[ApplicationMetrics]

  when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))

  // AppConfig mocks
  when(mockAppConfig.hipUrl).thenReturn("http://localhost:9999")
  when(mockAppConfig.hipAuthorisationToken).thenReturn("dGVzdC1hdXRo") // base64 encoded token
  when(mockAppConfig.originatorIdKey).thenReturn("gov-uk-originator-id")
  when(mockAppConfig.originatorIdValue).thenReturn("HMRC-GMP")
  when(mockAppConfig.hipEnvironmentHeader).thenReturn("Environment" -> "local")
  when(mockAppConfig.isHipEnabled).thenReturn(true)
  val validateSconResponseJson = Json.parse(
    """{
      |  "schemeContractedOutNumberExists": true
      |}""".stripMargin
  )

  object TestHipConnector extends HipConnector(appConfig = mockAppConfig, metrics = mockMetrics, http = mockHttp, auditConnector = mockAuditConnector)

  "HipConnector" should {
    "for validateScon" should {
      "return a valid response for HTTP 200" in {
        implicit val hc = HeaderCarrier()
        requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))

        val result = await(TestHipConnector.validateScon("user123", "S1401234Q"))

      result mustBe HipValidateSconResponse(true)
    }

      "return a valid response for HTTP 422" in {
        implicit val hc = HeaderCarrier()
        requestBuilderExecute(Future.successful(HttpResponse(422, validateSconResponseJson.toString())))

        val result = await(TestHipConnector.validateScon("user123", "S1401234Q"))

      result mustBe HipValidateSconResponse(true)
    }

      "throw UpstreamErrorResponse for error status codes (400, 403, 404, 500, 503)" in {
        val errorCodes = Seq(400, 403, 404, 500, 503)

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

      "throw IllegalArgumentException for invalid SCON" in {
        implicit val hc = HeaderCarrier()

        val ex = intercept[IllegalArgumentException] {
          await(TestHipConnector.validateScon("user123", "INVALID"))
        }

        ex.getMessage must include("Invalid SCON")
      }

      "log and continue if audit fails" in {
        implicit val hc = HeaderCarrier()

        when(mockAuditConnector.sendEvent(any())(any(), any()))
          .thenReturn(Future.failed(new RuntimeException("Audit failure")))

        requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))

        noException shouldBe thrownBy(await(TestHipConnector.validateScon("user123", "S1401234Q")))
      }

      "set required headers including correlationId and originator-id" in {
        implicit val hc = HeaderCarrier()

        val headersCaptor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])

        requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))
        await(TestHipConnector.validateScon("user123", "S1401234Q"))

        verify(requestBuilder, atLeastOnce()).setHeader(headersCaptor.capture(): _*)
        val captured = headersCaptor.getAllValues.asScala
        val headerMaps = captured.map(_.toMap)

        headerMaps.exists(_.contains("correlationId")) mustBe true
        headerMaps.exists(_.get(Constants.OriginatorIdKey).contains(mockAppConfig.originatorIdValue)) mustBe true
        headerMaps.exists(_.get("X-Originating-System").contains(Constants.XOriginatingSystemHeader)) mustBe true
        headerMaps.exists(_.get("X-Transmitting-System").contains(Constants.XTransmittingSystemHeader)) mustBe true
      }
    }
    "for post call" should {
      val calculateUrl: String = "http://localhost:9943/pensions/individuals/gmp/calculate"
      when(TestHipConnector.calcURI).thenReturn(calculateUrl)
      "return successful response for status 200" in {
        val request = HipCalculationRequest("", "S2123456B", "", "", Some(""),
          Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None,None, true, true)
        val successResponse = HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), Some(""), List.empty)
        implicit val hc = HeaderCarrier()
        val httpResponse = HttpResponse(OK, Json.toJson(successResponse).toString())
        requestBuilderExecute(Future.successful(httpResponse))
        await(TestHipConnector.calculate("user123", request)).map { result =>
          result.schemeContractedOutNumberDetails mustBe "S2123456B"
        }
      }
      "return a response for status 400" in {
        val request = HipCalculationRequest("", "S2123456B", "", "", Some(""),
          Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None,None, true, true)
        val successResponse = HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), Some(""), List.empty)
        val httpResponse = HttpResponse(BAD_REQUEST, Json.toJson(successResponse).toString())
        requestBuilderExecute(Future.successful(httpResponse))
        val exception = intercept[UpstreamErrorResponse] {
          await(TestHipConnector.calculate("user123", request))
        }
        exception.statusCode mustBe BAD_REQUEST
        exception.reportAs mustBe BAD_REQUEST
      }

      "return fallback HipCalculationResponse for 400 Bad Request" in {
        val successResponse = HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), Some(""), List.empty)
        val httpResponse = HttpResponse(BAD_REQUEST, Json.toJson(successResponse).toString())
        val request = HipCalculationRequest("S2123456B", "", "", "", Some(""),
          Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None,None, true, true)
        requestBuilderExecute(Future.successful(httpResponse))
        val exception = intercept[UpstreamErrorResponse] {
          await(TestHipConnector.calculate("user123", request))
        }
        exception.statusCode mustBe BAD_REQUEST
      }

      "fail the future if HTTP call fails" in {
        val request = HipCalculationRequest("S2123456B", "", "", "", Some(""),
          Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None,None, true, true)
        requestBuilderExecute(Future.failed(new RuntimeException("Connection error")))
        val ex = intercept[RuntimeException] {
          await(TestHipConnector.calculate("user123", request))
        }
        ex.getMessage must include("Connection error")
      }

      "throw UpstreamErrorResponse for error status code 500" in {
        val request = HipCalculationRequest("", "S2123456B", "", "", Some(""),
          Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None,None, true, true)
        val successResponse = HipCalculationResponse("", "S2123456B", Some(""), Some(""), Some(""), Some(""), List.empty)
        val httpResponse = HttpResponse(500, Json.toJson(successResponse).toString())
        implicit val hc = HeaderCarrier()
        requestBuilderExecute(Future.successful(httpResponse))
        val exception = intercept[UpstreamErrorResponse] {
          await(TestHipConnector.calculate("user123", request))
        }
        exception.statusCode mustBe 500
        exception.reportAs mustBe INTERNAL_SERVER_ERROR
      }

      "throw RuntimeException when JSON validation fails" in {
        val invalidJson = Json.obj("unexpectedField" -> "unexpectedValue")
        val httpResponse = HttpResponse(OK, invalidJson.toString())
        val request = HipCalculationRequest("", "S2123456B", "", "", Some(""),
          Some(EnumRevaluationRate.NONE), Some(EnumCalcRequestType.SPA), None,None, true, true)
        requestBuilderExecute(Future.successful(httpResponse))
        val thrown = intercept[RuntimeException] {
          await(TestHipConnector.calculate("user123", request))
        }
        thrown.getMessage must include("Failed to parse fields")
      }
    }
  }
}
