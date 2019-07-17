/*
 * Copyright 2019 HM Revenue & Customs
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

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import com.google.inject.{Inject, Singleton}
import config.{ApplicationConfig, WSHttp}
import metrics.ApplicationMetrics
import models._
import play.api.Mode.Mode
import play.api.http.Status._
import play.api.{Configuration, Logger, Play}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{DataEvent, EventTypes}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait DesGetResponse

sealed trait DesPostResponse

case object DesGetSuccessResponse extends DesGetResponse

case object DesGetHiddenRecordResponse extends DesGetResponse

case object DesGetNotFoundResponse extends DesGetResponse

case class DesGetErrorResponse(e: Exception) extends DesGetResponse

case object DesGetUnexpectedResponse extends DesGetResponse

@Singleton
class DesConnector @Inject()(val runModeConfiguration: Configuration,
                             metrics: ApplicationMetrics,
                             http: WSHttp,
                            auditConnector: AuditConnector)
  extends ApplicationConfig with RawResponseReads {

  override protected def mode: Mode = Play.current.mode

  private val PrefixStart = 0
  private val PrefixEnd = 1
  private val NumberStart = 1
  private val NumberEnd = 8
  private val SuffixStart = 8
  private val SuffixEnd = 9

  val serviceKey: String = getConfString("nps.key", "")
  val serviceEnvironment: String = getConfString("nps.environment", "")

  val baseURI = "pensions/individuals/gmp"
  val baseSconURI = "pensions/gmp/scon"
  val calcURI = s"$serviceURL/$baseURI"
  val validateSconURI = s"$serviceURL/$baseSconURI"
  lazy val serviceURL: String = baseUrl("nps")

  def citizenDetailsUrl: String = baseUrl("citizen-details")

  def validateScon(userId: String, scon: String)(implicit hc: HeaderCarrier): Future[ValidateSconResponse] = {

    val uri =
      s"""$validateSconURI/${
        scon.substring(PrefixStart,
          PrefixEnd).toUpperCase
      }/${
        scon.substring(NumberStart,
          NumberEnd)
      }/${
        scon.substring(SuffixStart,
          SuffixEnd).toUpperCase
      }/validate"""

    doAudit("gmpSconValidation", userId, scon, None, None, None)
    Logger.debug(s"[DesConnector][validateScon] Contacting DES at $uri")

    val startTime = System.currentTimeMillis()

    val result = http.GET[HttpResponse](uri)(hc = npsRequestHeaderCarrier, rds = httpReads, ec = ExecutionContext.global).map { response =>

      metrics.desConnectorTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
      metrics.desConnectorStatus(response.status)

      response.status match {
        case OK | UNPROCESSABLE_ENTITY => response.json.as[ValidateSconResponse]
        case errorStatus: Int => {
          Logger.error(s"[DesConnector][validateScon] : NPS returned code $errorStatus and response body: ${response.body}")
          throw new Upstream5xxResponse("DES connector validateScon failed", errorStatus, INTERNAL_SERVER_ERROR)
        }
      }

    }

    result
  }

  def calculate(userId: String, request: CalculationRequest)(implicit hc: HeaderCarrier): Future[CalculationResponse] = {

    val paramMap: Map[String, Option[Any]] = Map(
      "revalrate" -> request.revaluationRate, "revaldate" -> request.revaluationDate, "calctype" -> request.calctype,
      "request_earnings" -> request.requestEarnings, "dualcalc" -> request.dualCalc, "term_date" -> request.terminationDate)

    val surname = URLEncoder.encode((if (request.surname.replace(" ", "").length < 3) {
      request.surname.replace(" ", "")
    } else {
      request.surname.replace(" ", "").substring(0, 3)
    }).toUpperCase, "UTF-8")

    val firstname = URLEncoder.encode(request.firstForename.charAt(0).toUpper.toString, "UTF-8")

    val uri =
      s"""$calcURI/scon/${
        request.scon.substring(PrefixStart,
          PrefixEnd).toUpperCase
      }/${
        request.scon.substring(NumberStart,
          NumberEnd)
      }/${
        request.scon.substring(SuffixStart,
          SuffixEnd).toUpperCase
      }/nino/${request.nino.toUpperCase}/surname/$surname/firstname/$firstname/calculation/${
        buildEncodedQueryString(paramMap) match {
          case "?" => ""
          case params => params
        }
      }"""

    doAudit("gmpCalculation", userId, request.scon, Some(request.nino), Some(request.surname), Some(request.firstForename))
    Logger.debug(s"[DesConnector][calculate] Contacting DES at $uri")

    val startTime = System.currentTimeMillis()

    val result = http.GET[HttpResponse](uri)(hc = npsRequestHeaderCarrier, rds = httpReads, ec = ExecutionContext.global).map { response =>

      metrics.desConnectorTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
      metrics.desConnectorStatus(response.status)

      response.status match {
        case OK | UNPROCESSABLE_ENTITY => response.json.as[CalculationResponse]
        case BAD_REQUEST => {
          Logger.info("[DesConnector][calculate] : NPS returned code 400")
          CalculationResponse(request.nino,
            400,
            None,
            None,
            None,
            Scon(request.scon.substring(PrefixStart, PrefixEnd).toUpperCase, request.scon.substring(NumberStart, NumberEnd).toInt, request.scon.substring(SuffixStart, SuffixEnd).toUpperCase),
            Nil)
        }
        case errorStatus: Int => {
          Logger.error(s"[DesConnector][calculate] : NPS returned code $errorStatus and response body: ${response.body}")
          throw new Upstream5xxResponse("DES connector calculate failed", errorStatus, INTERNAL_SERVER_ERROR)
        }
      }
    }

    result

  }

  private def npsRequestHeaderCarrier(implicit hc: HeaderCarrier): HeaderCarrier =
    HeaderCarrier(extraHeaders = Seq(
      "Gov-Uk-Originator-Id" -> getConfString("nps.originator-id", ""),
      "Authorization" -> s"Bearer $serviceKey",
      "Environment" -> serviceEnvironment))

  private def buildEncodedQueryString(params: Map[String, Any]): String = {
    val encoded = for {
      (name, value) <- params if value != None
      encodedValue = value match {
        case Some(x) => URLEncoder.encode(x.toString, "UTF8")
      }
    } yield name + "=" + encodedValue

    encoded.mkString("?", "&", "")
  }

  private def doAudit(auditTag: String,
                      userID: String,
                      scon: String,
                      nino: Option[String],
                      surname: Option[String],
                      firstForename: Option[String])(implicit hc: HeaderCarrier): Unit = {

    val auditDetails: Map[String, String] = Map("userId" -> userID,
      "scon" -> scon,
      "nino" -> nino.getOrElse(""),
      "firstName" -> firstForename.getOrElse(""),
      "surname" -> surname.getOrElse(""))

    val auditResult = auditConnector.sendEvent(
      DataEvent("gmp",
        EventTypes.Succeeded,
        tags = hc.toAuditTags(auditTag, "N/A"),
        detail = hc.toAuditDetails() ++ auditDetails))

    auditResult.onFailure {
      case e: Throwable => Logger.warn("[DesConnector][doAudit] : auditResult: " + e.getMessage, e)
    }
  }

  def getPersonDetails(nino: String)(implicit hc: HeaderCarrier): Future[DesGetResponse] = {

    val newHc = HeaderCarrier(extraHeaders = Seq(
      "Gov-Uk-Originator-Id" -> getConfString("des.originator-id", ""),
      "Authorization" -> s"Bearer $serviceKey",
      "Environment" -> serviceEnvironment))

    val startTime = System.currentTimeMillis()
    val url = s"$citizenDetailsUrl/citizen-details/$nino/etag"

    Logger.debug(s"[DesConnector][getPersonDetails] Retrieving person details from $url")

    http.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], newHc, ec = ExecutionContext.global) map { response =>

      metrics.mciConnectionTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)

      response.status match {
        case LOCKED =>
          metrics.mciLockCount()
          DesGetHiddenRecordResponse
        case NOT_FOUND => DesGetNotFoundResponse
        case OK => DesGetSuccessResponse
        case INTERNAL_SERVER_ERROR => DesGetUnexpectedResponse
        case _ => DesGetUnexpectedResponse
      }

    } recover {
      case e: NotFoundException => DesGetNotFoundResponse
      case e: Exception =>
        Logger.error("[DesConnector][getPersonDetails] Exception thrown getting individual record from DES", e)
        metrics.mciErrorCount()
        DesGetErrorResponse(e)
    }
  }

}
