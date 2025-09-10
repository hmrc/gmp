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
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.libs.json.{JsError, JsSuccess, Json}
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
import scala.util.{Failure, Success}

@Singleton
class HipConnector @Inject()(
                              appConfig: AppConfig,
                              metrics: ApplicationMetrics,
                              http: HttpClientV2,
                              auditConnector: AuditConnector
                            )(implicit ec: ExecutionContext) extends Logging {

  val hipBaseUrl: String = appConfig.hipUrl
  val calcURI = s"$hipBaseUrl/ni/gmp/calculation"
  private val sconPattern = "^S[0124568]\\d{6}(?![GIOSUVZ])[A-Z]$".r

  def validateScon(userId: String, scon: String)(implicit hc: HeaderCarrier): Future[HipValidateSconResponse] = {

      val formattedScon = normalizeScon(scon)
      val url = s"$hipBaseUrl/ni/gmp/$formattedScon/validate"

      val headers = buildHeadersV1

      doAudit("hipSconValidation", userId, formattedScon, None, None, None)

      val startTime = System.currentTimeMillis()

      http.get(url"$url")
        .setHeader(headers: _*)
        .execute[HttpResponse]
        .map { response =>
          val duration = System.currentTimeMillis() - startTime
          metrics.hipConnectorTimer(duration, TimeUnit.MILLISECONDS)
          metrics.hipConnectorStatus(response.status)
          response.status match {
            case Status.OK  =>
              response.json.as[HipValidateSconResponse]

            case Status.BAD_REQUEST | Status.FORBIDDEN | Status.NOT_FOUND | Status.INTERNAL_SERVER_ERROR | Status.SERVICE_UNAVAILABLE =>
              throw UpstreamErrorResponse(response.body, response.status, Status.INTERNAL_SERVER_ERROR)

            case other =>
              throw UpstreamErrorResponse("HIP connector validateScon unexpected response", other, Status.INTERNAL_SERVER_ERROR)
          }
        }
  }

  private def normalizeScon(rawScon: String): String = {
    val scon = rawScon.replaceAll("\\s+", "").toUpperCase
    scon match {
      case sconPattern(_*) => scon
      case _ =>
        throw new IllegalArgumentException(s"Invalid SCON: '$rawScon'")
    }
  }

  def calculate(userId: String, request: HipCalculationRequest)(implicit hc: HeaderCarrier): Future[HipCalculationResponse] = {
    logger.info(s"calculate url for HipConnector:$calcURI")
    logger.info(s"[HipConnector calculate request body]:${Json.toJson(request)}")
    doAudit("gmpCalculation", userId, request.schemeContractedOutNumber, Some(request.nationalInsuranceNumber),
      Some(request.surname), Some(request.firstForename))
    val startTime = System.currentTimeMillis()
    val headers = buildHeadersV1
    logger.info(s"[HipConnector] Headers being set: ${headers.mkString(", ")}")

    http.post(url"$calcURI")
      .setHeader(headers: _*)
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map { response =>
        logger.info(s"[HipConnector calculate response]:${response.status}:::::${response.headers.get("correlationId")}:::::${response.json}")
        metrics.hipConnectorTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
        metrics.hipConnectorStatus(response.status)

        response.status match {
          case OK =>
            response.json.validate[HipCalculationResponse] match {
              case JsSuccess(value, _) => value
              case JsError(errors) =>
                val errorFields = errors.map(_._1.toString()).mkString(", ")
                val responseBody = response.body.take(1000)
                val detailedMsg = s"HIP returned invalid JSON (status: ${response.status}). Failed to parse fields: $errorFields. Body: $responseBody"
                logger.error(s"[HipConnector][calculate] $detailedMsg")
                throw new RuntimeException(detailedMsg)
            }
          case status =>
            val (logLevel, message, reportAs) = status match {
              case BAD_REQUEST =>
                (logger.warn(_: String), "Bad Request", BAD_REQUEST)
              case FORBIDDEN =>
                (logger.warn(_: String), "Forbidden", FORBIDDEN)
              case NOT_FOUND =>
                (logger.warn(_: String), "Not Found", NOT_FOUND)
              case _ if status >= 500 =>
                (logger.error(_: String), s"Unexpected error (Status: $status)", INTERNAL_SERVER_ERROR)
              case _ =>
                (logger.warn(_: String), s"Client error (Status: $status)", status)
            }

            logLevel(s"[HipConnector][calculate] : HIP returned $status ($message). Body: ${response.body}")
            throw UpstreamErrorResponse(
              message = s"HIP connector calculate failed: $message",
              statusCode = status,
              reportAs = reportAs
            )
        }
      }
  }

  private def getCorrelationId: String = UUID.randomUUID().toString


  private def buildHeadersV1: Seq[(String, String)] =
    Seq(
      Constants.OriginatorIdKey       -> appConfig.originatorIdValue,
      "correlationId"                 -> getCorrelationId,
      "Authorization"                 -> s"Basic ${appConfig.hipAuthorisationToken}",
      appConfig.hipEnvironmentHeader,
      "X-Originating-System"         -> Constants.XOriginatingSystemHeader,
      "X-Receipt-Date"               -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)),
      "X-Transmitting-System"        -> Constants.XTransmittingSystemHeader
    )



  private def doAudit(
                       auditTag: String,
                       userID: String,
                       scon: String,
                       nino: Option[String],
                       surname: Option[String],
                       firstForename: Option[String]
                     )(implicit hc: HeaderCarrier): Unit = {
    val correlationId = hc.requestId.map(_.value).orElse(hc.sessionId.map(_.value)).getOrElse("unknown")
    val auditDetails: Map[String, String] = Map(
      "userId" -> userID,
      "scon" -> scon,
      "nino" -> nino.getOrElse(""),
      "firstName" -> firstForename.getOrElse(""),
      "surname" -> surname.getOrElse(""),
      "correlationId" -> correlationId
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
