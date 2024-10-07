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

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import com.google.inject.{Inject, Singleton}
import metrics.ApplicationMetrics
import models._
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{DataEvent, EventTypes}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2

trait IFGetResponse


case object IFGetSuccessResponse extends IFGetResponse

case object IFGetHiddenRecordResponse extends IFGetResponse

case object IFGetNotFoundResponse extends IFGetResponse

case class IFGetErrorResponse(e: Exception) extends IFGetResponse

case object IFGetUnexpectedResponse extends IFGetResponse

@Singleton
class IFConnector @Inject()(val runModeConfiguration: Configuration,
                             metrics: ApplicationMetrics,
                             http: HttpClientV2,
                             auditConnector: AuditConnector,
                             val servicesConfig: ServicesConfig)(implicit ec: ExecutionContext) extends Logging {

  private val PrefixStart = 0
  private val PrefixEnd = 1
  private val NumberStart = 1
  private val NumberEnd = 8
  private val SuffixStart = 8
  private val SuffixEnd = 9

  val serviceKey: String = servicesConfig.getConfString("ifs.key", "")
  val serviceEnvironment: String = servicesConfig.getConfString("ifs.environment", "")

  val baseURI = "pensions/individuals/gmp"
  val baseSconURI = "pensions/gmp/scon"
  val calcURI = s"$serviceURL/$baseURI"
  val validateSconURI = s"$serviceURL/$baseSconURI"
  lazy val serviceURL: String = servicesConfig.baseUrl("ifs")

  val citizenDetailsUrl: String = servicesConfig.baseUrl("citizen-details")

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
    logger.debug(s"[IFConnector][validateScon] Contacting IF at $uri")

    val startTime = System.currentTimeMillis()

    val result = http.get(url"$uri")
      .withBody(Json.toJson(IFHeaders))
      .execute[HttpResponse]
      .map { response =>
      metrics.IFConnectorTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
      metrics.IFConnectorStatus(response.status)

      response.status match {
        case OK | UNPROCESSABLE_ENTITY => response.json.as[ValidateSconResponse]
        case errorStatus: Int => {
          logger.error(s"[IFConnector][validateScon] : IF returned code $errorStatus and response body: ${response.body}")
          throw UpstreamErrorResponse("IF connector validateScon failed", errorStatus, INTERNAL_SERVER_ERROR)
        }
      }

    }

    result
  }

  def calculate(userId: String, request: CalculationRequest)(implicit hc: HeaderCarrier): Future[CalculationResponse] = {

    val paramMap: Map[String, Option[Any]] = Map(
      "revalrate" -> request.revaluationRate, "revaldate" -> request.revaluationDate, "calctype" -> request.calctype,
      "request_earnings" -> request.requestEarnings, "dualcalc" -> request.dualCalc, "term_date" -> request.terminationDate)

    val surname = URLEncoder.encode((if (request.surname.length < 3) {
      request.surname
    } else {
      request.surname.substring(0, 3)
    }).toUpperCase.trim, "UTF-8")

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
    logger.debug(s"[IFConnector][calculate] Contacting IF at $uri")

    val startTime = System.currentTimeMillis()

    val result = http.get(url"$uri")
      .withBody(Json.toJson(IFHeaders))
      .execute[HttpResponse]
      .map { response =>
      metrics.IFConnectorTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)
      metrics.IFConnectorStatus(response.status)

      response.status match {
        case OK | UNPROCESSABLE_ENTITY => response.json.as[CalculationResponse]
        case BAD_REQUEST => {
          logger.info("[IFConnector][calculate] : IF returned code 400")
          CalculationResponse(request.nino,
            BAD_REQUEST,
            None,
            None,
            None,
            Scon(request.scon.substring(PrefixStart, PrefixEnd).toUpperCase, request.scon.substring(NumberStart, NumberEnd).toInt, request.scon.substring(SuffixStart, SuffixEnd).toUpperCase),
            Nil)
        }
        case errorStatus: Int => {
          logger.error(s"[IFConnector][calculate] : IF returned code $errorStatus and response body: ${response.body}")
          throw UpstreamErrorResponse("IF connector calculate failed", errorStatus, INTERNAL_SERVER_ERROR)
        }
      }
    }

    result

  }

  private def IFHeaders =
    Seq("Authorization" -> s"Bearer $serviceKey",
      "Gov-Uk-Originator-Id" -> servicesConfig.getConfString("ifs.originator-id", ""),
      "Environment" -> serviceEnvironment)

  private def buildEncodedQueryString(params: Map[String, Option[Any]]): String = {
    val encoded = for {
      (name, value) <- params if value.isDefined
      encodedValue = URLEncoder.encode(value.get.toString, "UTF8")
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

    auditResult.failed.foreach({
      e: Throwable => logger.warn("[IFConnector][doAudit] : auditResult: " + e.getMessage, e)
    })
  }

  def getPersonDetails(nino: String)(implicit hc: HeaderCarrier): Future[IFGetResponse] = {

    val startTime = System.currentTimeMillis()
    val url = s"$citizenDetailsUrl/citizen-details/$nino/etag"

    logger.debug(s"[IFConnector][getPersonDetails] Retrieving person details from $url")

    http.get(url"$url")
      .withBody(Json.toJson(IFHeaders))
        .execute[HttpResponse]
        .map { response =>
      metrics.mciConnectionTimer(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS)

      response.status match {
        case LOCKED =>
          metrics.mciLockCount()
          IFGetHiddenRecordResponse
        case NOT_FOUND => IFGetNotFoundResponse
        case OK => IFGetSuccessResponse
        case INTERNAL_SERVER_ERROR => IFGetUnexpectedResponse
        case _ => IFGetUnexpectedResponse
      }

    } recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => IFGetNotFoundResponse
      case e: Exception =>
        logger.error("[IFConnector][getPersonDetails] Exception thrown getting individual record from IF", e)
        metrics.mciErrorCount()
        IFGetErrorResponse(e)
    }
  }

}
