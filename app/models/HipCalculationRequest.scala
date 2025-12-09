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

package models

import play.api.libs.json._

import java.net.URLEncoder
import java.time.LocalDate

object EnumRevaluationRate extends Enumeration {
  type EnumRevaluationRate = Value
  val NONE = Value("(NONE)")
  val FIXED = Value("FIXED")
  val LIMITED= Value("LIMITED")
  val S148 = Value("S148")

  implicit val format: Format[EnumRevaluationRate.Value] = new Format[EnumRevaluationRate.Value] {
    def writes(enumRevalRate: EnumRevaluationRate.Value): JsValue = JsString(enumRevalRate.toString)

    def reads(json: JsValue): JsResult[EnumRevaluationRate.Value] = json match {
      case JsString(str) if EnumRevaluationRate.values.exists(_.toString == str) => JsSuccess(EnumRevaluationRate.withName(str))
      case _ => JsError(s"Invalid EnumRevaluationRate:$json")
    }
  }
}

object EnumCalcRequestType extends Enumeration {
  type EnumCalcRequestType = Value
  val DOL = Value("DOL Calculation")
  val Revaluation = Value("Re-valuation Calculation")
  val PayableAge = Value("Payable Age Calculation")
  val Survivor = Value("Survivor Calculation")
  val SPA = Value("SPA Calculation")

  implicit val format: Format[EnumCalcRequestType.Value] = new Format[EnumCalcRequestType.Value] {
    def writes(enumCalcReqType: EnumCalcRequestType.Value): JsValue = JsString(enumCalcReqType.toString)

    def reads(json: JsValue): JsResult[EnumCalcRequestType.Value] = json match {
      case JsString(str) if EnumCalcRequestType.values.exists(_.toString == str) => JsSuccess(EnumCalcRequestType.withName(str))
      case _ => JsError(s"Invalid EnumCalcRequestType:$json")
    }
  }
}

case class HipCalculationRequest(schemeContractedOutNumber: String,
                                 nationalInsuranceNumber: String,
                                 surname: String,
                                 firstForename: String,
                                 secondForename: Option[String],
                                 revaluationRate: Option[EnumRevaluationRate.Value],
                                 calculationRequestType: Option[EnumCalcRequestType.Value],
                                 revaluationDate: Option[LocalDate],
                                 terminationDate: Option[LocalDate],
                                 includeContributionAndEarnings: Boolean,
                                 includeDualCalculation: Boolean)


object HipCalculationRequest {
  implicit val localDateFormat: Format[LocalDate] =
    Format[LocalDate](Reads.localDateReads("yyyy-MM-dd"), Writes.temporalWrites[LocalDate, String]("yyyy-MM-dd"))

  implicit val formats: OFormat[HipCalculationRequest] = Json.format[HipCalculationRequest]

  // Constants for revaluation rate mapping
  private val RevaluationRateS148 = 1
  private val RevaluationRateFixed = 2
  private val RevaluationRateLimited = 3

  // Constants for calculation type mapping
  private val CalculationTypeDOL = 0
  private val CalculationTypeRevaluation = 1
  private val CalculationTypePayableAge = 2
  private val CalculationTypeSurvivor = 3
  private val CalculationTypeSPA = 4

  def from(calcReq: CalculationRequest): HipCalculationRequest = {
    val revalEnum = calcReq.revaluationRate.map {
      case RevaluationRateS148 => EnumRevaluationRate.S148
      case RevaluationRateFixed => EnumRevaluationRate.FIXED
      case RevaluationRateLimited => EnumRevaluationRate.LIMITED
      case _ => EnumRevaluationRate.NONE
    }
    val calcTypeEnum = calcReq.calctype.map {
      case CalculationTypeDOL => EnumCalcRequestType.DOL
      case CalculationTypeRevaluation => EnumCalcRequestType.Revaluation
      case CalculationTypePayableAge => EnumCalcRequestType.PayableAge
      case CalculationTypeSurvivor => EnumCalcRequestType.Survivor
      case CalculationTypeSPA => EnumCalcRequestType.SPA
    }
    HipCalculationRequest(
      schemeContractedOutNumber = calcReq.scon,
      nationalInsuranceNumber = calcReq.nino.toUpperCase.trim,
      surname = URLEncoder.encode(calcReq.surname.take(3).toUpperCase.trim, "UTF-8"),
      firstForename = URLEncoder.encode(calcReq.firstForename.charAt(0).toUpper.toString, "UTF-8"),
      secondForename = None,
      revaluationRate = revalEnum,
      calculationRequestType = calcTypeEnum,
      revaluationDate = calcReq.revaluationDate.map(LocalDate.parse),
      terminationDate = calcReq.terminationDate.map(LocalDate.parse),
      includeContributionAndEarnings = calcReq.requestEarnings.contains(1),
      includeDualCalculation = calcReq.dualCalc.contains(1)
    )
  }
}
