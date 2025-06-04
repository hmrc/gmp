package connectors

import metrics.ApplicationMetrics
import models.ValidateSconResponse
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala

class HipConnectorSpec extends HttpClientV2Helper {

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockServicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]
  val config: Configuration = app.injector.instanceOf[Configuration]

  when(mockAuditConnector.sendEvent(any())(any(), any()))
    .thenReturn(Future.successful(AuditResult.Success))

  val mockMetrics: ApplicationMetrics = mock[ApplicationMetrics]

  object TestHipConnector extends HipConnector(
    config = mockServicesConfig,
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

    "successfully return a ValidateSconResponse for status 200" in {
      implicit val hc = HeaderCarrier()

      requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))

      val result = TestHipConnector.validateScon("user123", "S1401234Q")
      val response = await(result)

      response mustBe ValidateSconResponse(1)
    }

    "successfully return a ValidateSconResponse for status 422" in {
      implicit val hc = HeaderCarrier()

      requestBuilderExecute(Future.successful(HttpResponse(422, validateSconResponseJson.toString())))

      val result = TestHipConnector.validateScon("user123", "S1401234Q")
      val response = await(result)

      response mustBe ValidateSconResponse(1)
    }

    "throw UpstreamErrorResponse for 400 response" in {
      implicit val hc = HeaderCarrier()

      requestBuilderExecute(Future.successful(HttpResponse(400, "Bad Request")))

      intercept[UpstreamErrorResponse] {
        await(TestHipConnector.validateScon("user123", "S1401234Q"))
      }.statusCode mustBe 400
    }

    "throw UpstreamErrorResponse for 403, 404, 500, 503 responses" in {
      val errorStatuses = Seq(403, 404, 500, 503)

      errorStatuses.foreach { status =>
        implicit val hc = HeaderCarrier()
        requestBuilderExecute(Future.successful(HttpResponse(status, "Error")))

        val exception = intercept[UpstreamErrorResponse] {
          await(TestHipConnector.validateScon("user123", "S1401234Q"))
        }

        exception.statusCode mustBe status
        exception.reportAs mustBe 500
      }
    }

    "throw an error for invalid SCON format" in {
      implicit val hc = HeaderCarrier()

      val ex = intercept[IllegalArgumentException] {
        await(TestHipConnector.validateScon("user123", "S14012349898Q"))
      }

      ex.getMessage must include("Invalid SCON")
    }

    "log and handle audit failure" in {
      implicit val hc = HeaderCarrier()

      when(mockAuditConnector.sendEvent(any())(any(), any()))
        .thenReturn(Future.failed(new RuntimeException("Audit failed")))

      requestBuilderExecute(Future.successful(HttpResponse(200, validateSconResponseJson.toString())))

      await(TestHipConnector.validateScon("user123", "S1401234Q"))
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
      headerMaps.exists(_.contains("gov-uk-originator-id")) mustBe true

    }
  }
}
