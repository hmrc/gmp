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
import models.ValidateSconResponse
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class HipConnectorSpec extends HttpClientV2Helper {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockMetrics: ApplicationMetrics = mock[ApplicationMetrics]

  when(mockAuditConnector.sendEvent(any())(any(), any()))
    .thenReturn(Future.successful(AuditResult.Success))

  // AppConfig mocks
  when(mockAppConfig.hipUrl).thenReturn("http://localhost:9999")
  when(mockAppConfig.hipAuthorisationToken).thenReturn("dGVzdC1hdXRo") // base64 encoded token
  when(mockAppConfig.originatorIdKey).thenReturn("gov-uk-originator-id")
  when(mockAppConfig.originatorIdValue).thenReturn("HMRC-GMP")
  when(mockAppConfig.hipEnvironmentHeader).thenReturn("Environment" -> "local")
  when(mockAppConfig.isHipEnabled).thenReturn(true)

  object TestHipConnector extends HipConnector(
    appConfig = mockAppConfig,
    metrics = mockMetrics,
    http = mockHttp,
    auditConnector = mockAuditConnector
  )

  val validateSconResponseJson = Json.parse(
    """{
      |  "scon_exists": 1
      |}""".stripMargin
  )

  before {
    reset(mockHttp)
    when(mockHttp.get(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  }

  "HipConnector" should {

    "return a valid response for HTTP 200" in {
      implicit val hc = HeaderCarrier()
      requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))

      val result = await(TestHipConnector.validateScon("user123", "S1401234Q"))

      result mustBe ValidateSconResponse(1)
    }

    "return a valid response for HTTP 422" in {
      implicit val hc = HeaderCarrier()
      requestBuilderExecute(Future.successful(HttpResponse(422, validateSconResponseJson.toString())))

      val result = await(TestHipConnector.validateScon("user123", "S1401234Q"))

      result mustBe ValidateSconResponse(1)
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
}
