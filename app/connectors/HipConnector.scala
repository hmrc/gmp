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

import com.google.inject.{Inject, Singleton}
import config.{AppConfig, Constants}
import metrics.ApplicationMetrics
import models._
import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{DataEvent, EventTypes}
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HipConnector @Inject()(
                              appConfig: AppConfig,
                              metrics: ApplicationMetrics,
                              http: HttpClientV2,
                              auditConnector: AuditConnector
                            )(implicit ec: ExecutionContext) extends Logging {

  val hipBaseUrl: String = appConfig.hipUrl

  def validateScon(userId: String, scon: String)(implicit hc: HeaderCarrier): Future[HipValidateSconResponse] = {
    if (appConfig.isHipEnabled) {
      val formattedScon = normalizeScon(scon)
      val url = s"$hipBaseUrl/ni/gmp/$formattedScon/validate"

      val headers = buildHeadersV1(hc)

      doAudit("hipSconValidation", userId, formattedScon, None, None, None)

      logger.debug(s"[HipConnector][validateScon] Contacting HIP at $url with headers: $headers")

      val startTime = System.currentTimeMillis()

      logger.info(s"[HipConnector] Headers being set: ${headers.mkString(", ")}")


      http.get(url"$url")
        .setHeader(headers: _*)
        .execute[HttpResponse]
        .map { response =>
          metrics.hipConnectorTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
          metrics.hipConnectorStatus(response.status)
          logger.info(s"[HipConnector] Headers being set: ${headers.mkString(", ")}")
          response.status match {
            case Status.OK | Status.UNPROCESSABLE_ENTITY =>
              response.json.as[HipValidateSconResponse]

            case Status.BAD_REQUEST | Status.FORBIDDEN | Status.NOT_FOUND | Status.INTERNAL_SERVER_ERROR | Status.SERVICE_UNAVAILABLE =>
              throw UpstreamErrorResponse(response.body, response.status, Status.INTERNAL_SERVER_ERROR)

            case other =>
              throw UpstreamErrorResponse("HIP connector validateScon unexpected response", other, Status.INTERNAL_SERVER_ERROR)
          }
        }
    } else {
      Future.failed(new IllegalStateException("HIP feature is disabled. validateScon cannot be called."))
    }
  }

  private def getCorrelationId(hc: HeaderCarrier): String =
    UUID.randomUUID().toString

  private def buildHeadersV1(hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      Constants.OriginatorIdKey       -> appConfig.originatorIdValue,
      "correlationId"                 -> getCorrelationId(hc),
      "Authorization"                 -> s"Basic ${appConfig.hipAuthorisationToken}",
      appConfig.hipEnvironmentHeader,
      "X-Originating-System"         -> Constants.XOriginatingSystemHeader,
      "X-Receipt-Date"               -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)),
      "X-Transmitting-System"        -> Constants.XTransmittingSystemHeader
    )
  private val sconPattern = """^([S]?([0124568])\d{6}(?![GIOSUVZ])[A-Z]?)$""".r

  private def normalizeScon(rawScon: String): String = {
    val scon = rawScon.replaceAll("\\s+", "").toUpperCase
    scon match {
      case sconPattern(_*) => scon
      case _ =>
        throw new IllegalArgumentException(s"Invalid SCON: '$rawScon'")
    }
  }

  private def doAudit(
                       auditTag: String,
                       userID: String,
                       scon: String,
                       nino: Option[String],
                       surname: Option[String],
                       firstForename: Option[String]
                     )(implicit hc: HeaderCarrier): Unit = {
    val auditDetails: Map[String, String] = Map(
      "userId" -> userID,
      "scon" -> scon,
      "nino" -> nino.getOrElse(""),
      "firstName" -> firstForename.getOrElse(""),
      "surname" -> surname.getOrElse("")
    )

    auditConnector.sendEvent(
      DataEvent(
        auditSource = "gmp",
        auditType = EventTypes.Succeeded,
        tags = hc.toAuditTags(auditTag, "N/A"),
        detail = hc.toAuditDetails() ++ auditDetails
      )
    ).failed.foreach {
      case e: Throwable => logger.warn("[HipConnector][doAudit] Audit failed", e)
    }
  }
}
