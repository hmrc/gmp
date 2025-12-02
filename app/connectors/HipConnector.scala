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
import models.{HipCalcResult, HipCalculationFailuresResponse, HipCalculationRequest, HipCalculationResponse, HipValidateSconResponse}
import models.HipCalcResult.{Failures, Success}
import play.api.Logging
import play.api.http.Status.{BAD_GATEWAY, BAD_REQUEST, FORBIDDEN, NOT_FOUND, INTERNAL_SERVER_ERROR, OK, SERVICE_UNAVAILABLE, UNPROCESSABLE_ENTITY}
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.AuditExtensions.*
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{DataEvent, EventTypes}
import play.api.libs.ws.JsonBodyWritables.*
import utils.LoggingUtils

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

  private val hipBaseUrl: String = appConfig.hipUrl
  
  val calcURI = s"$hipBaseUrl/ni/gmp/calculation"
  private val MaxBodyLengthForLogging = 300
  private val sconPattern = "^S[0124568]\\d{6}(?![GIOSUVZ])[A-Z]$".r

  def validateScon(userId: String, scon: String)(implicit hc: HeaderCarrier): Future[HipValidateSconResponse] = {
    // First validate the SCON format - this will throw IllegalArgumentException for invalid formats
    val formattedScon = normalizeScon(scon)

    val url = s"$hipBaseUrl/ni/gmp/$formattedScon/validate"
    val headers = buildHeadersV1

    doAudit("hipSconValidation", userId, formattedScon, None, None, None)

    val startTime = System.currentTimeMillis()

    http.get(url"$url")
      .setHeader(headers *)
      .execute[HttpResponse]
      .map { response =>
        val duration = System.currentTimeMillis() - startTime
        metrics.hipConnectorTimer(duration, TimeUnit.MILLISECONDS)
        metrics.hipConnectorStatus(response.status)
        response.status match {
          case OK  => response.json.as[HipValidateSconResponse]

          case BAD_REQUEST | FORBIDDEN | NOT_FOUND | INTERNAL_SERVER_ERROR | SERVICE_UNAVAILABLE =>
            throw UpstreamErrorResponse(response.body, response.status, INTERNAL_SERVER_ERROR)

          case other =>
            throw UpstreamErrorResponse("HIP connector validateScon unexpected response", other, INTERNAL_SERVER_ERROR)
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

  def calculate(userId: String, request: HipCalculationRequest)(implicit hc: HeaderCarrier): Future[HipCalcResult] = {

    doAudit(
      "gmpCalculation",
      userId,
      request.schemeContractedOutNumber,
      Some(request.nationalInsuranceNumber),
      Some(request.surname),
      Some(request.firstForename)
    )

    val startTime = System.currentTimeMillis()
    val headers   = buildHeadersV1

    http
      .post(url"$calcURI")
      .setHeader(headers *)
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map { response =>
        val took = System.currentTimeMillis() - startTime
        metrics.hipConnectorTimer(took, TimeUnit.MILLISECONDS)
        metrics.hipConnectorStatus(response.status)

        val cid = response.headers.get("correlationId").flatMap(_.headOption).getOrElse("n/a")
        logger.debug(s"[HipConnector][calculate] Response status: ${response.status}, correlationId: $cid")

        response.status match {
          case OK => Success(parseHipCalculationSuccess(response))
          case UNPROCESSABLE_ENTITY | BAD_REQUEST | FORBIDDEN | INTERNAL_SERVER_ERROR | SERVICE_UNAVAILABLE =>
            Failures(parseHipFailures(response), response.status)
          case unexpectedStatus =>
            throw upstreamErrorResponse(response, s"HIP connector calculate failed with status $unexpectedStatus")
        }
      }
  }

  private def parseHipCalculationSuccess(response: HttpResponse): HipCalculationResponse = {
    // Only now parse JSON; log redacted raw body safely
    logger.debug(s"[HipConnector][calculate] Response body: ${LoggingUtils.redactCalculationData(response.body)}")

    scala.util.Try(Json.parse(response.body)).toOption match {
      case Some(js) =>
        js.validate[HipCalculationResponse] match {
          case JsSuccess(value, _) => value
          case JsError(errors) =>
            val errorFields = errors.map(_._1.toString()).mkString(", ")
            val detailedMsg = s"HIP returned invalid JSON (status: ${response.status}). Failed to parse fields: $errorFields"
            logger.error(s"[HipConnector][calculate] $detailedMsg")
            throw UpstreamErrorResponse(detailedMsg, BAD_GATEWAY, BAD_GATEWAY)
        }
      case None =>
        val detailedMsg = s"HIP returned non-JSON body with ${response.status}. Body: ${response.body.take(MaxBodyLengthForLogging)}"
        logger.error(s"[HipConnector][calculate] $detailedMsg")
        throw UpstreamErrorResponse(detailedMsg, BAD_GATEWAY, BAD_GATEWAY)
    }
  }

  private def parseHipFailures(response: HttpResponse): models.HipCalculationFailuresResponse = {
    val status = response.status
    logger.debug(s"[HipConnector][calculate][$status] Response body: ${LoggingUtils.redactCalculationData(response.body)}")
    scala.util.Try(Json.parse(response.body)).toOption match {
      case Some(js) =>
        js.validate[models.HipCalculationFailuresResponse] match {
          case JsSuccess(value, _) => value
          case JsError(errors) =>
            val errorFields = errors.map(_._1.toString()).mkString(", ")
            val detailedMsg = s"HIP $status returned invalid JSON. Failed to parse fields: $errorFields"
            logger.error(s"[HipConnector][calculate] $detailedMsg")
            throw upstreamErrorResponse(response, detailedMsg)
        }
      case None =>
        val detailedMsg = s"HIP returned non-JSON body with $status. Body: ${response.body.take(MaxBodyLengthForLogging)}"
        logger.error(s"[HipConnector][calculate] $detailedMsg")
        throw upstreamErrorResponse(response, detailedMsg)
    }
  }

  private def upstreamErrorResponse(response: HttpResponse, detailedMsg: String): UpstreamErrorResponse = {
    val reportAs = response.status match {
      case UNPROCESSABLE_ENTITY => BAD_GATEWAY
      case s if s >= 500 => INTERNAL_SERVER_ERROR
      case s => s
    }
    UpstreamErrorResponse(detailedMsg, response.status, reportAs, response.headers)
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
