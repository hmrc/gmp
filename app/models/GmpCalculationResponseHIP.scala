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

package models

import java.time.LocalDate
import play.api.libs.json._


case class ContributionsAndEarningsHIP(taxYear: Int, contEarnings: String)

object ContributionsAndEarningsHIP {
  implicit val formats: OFormat[ContributionsAndEarningsHIP] = Json.format[ContributionsAndEarningsHIP]

  //HIP Transformation
  def createFromHipDetails(details: ContributionsAndEarningsDetails): ContributionsAndEarningsHIP = {
    ContributionsAndEarningsHIP(details.taxYear, details.taxYear match {
      case x if x < 1987 => f"${details.contributionOrEarningsAmount}%1.2f"
      case _ => {
        val formatter = java.text.NumberFormat.getIntegerInstance
        formatter.format(details.contributionOrEarningsAmount)
      }
    })
  }
}

case class CalculationPeriodHIP(startDate: Option[LocalDate],
                             endDate: LocalDate,
                             gmpTotal: String,
                             post88GMPTotal: String,
                             revaluationRate: String,
                             errorCode: String,
                             revalued: Option[Int],
                             dualCalcPost90TrueTotal: Option[String],
                             dualCalcPost90OppositeTotal: Option[String],
                             inflationProofBeyondDod: Option[Int],
                             contsAndEarnings: Option[List[ContributionsAndEarningsHIP]]
                            )

object CalculationPeriodHIP {
  implicit val formats: OFormat[CalculationPeriodHIP] = Json.format[CalculationPeriodHIP]

  //HIP Transformation
  def createFromHipGmpDetails(details: GuaranteedMinimumPensionDetails): CalculationPeriodHIP = {
    CalculationPeriodHIP(
      startDate = details.schemeMembershipStartDate.map(LocalDate.parse(_)),
      endDate = LocalDate.parse(details.schemeMembershipEndDate),
      gmpTotal = f"${details.gmpContractedOutDeductionsAllRateValue}%1.2f",
      post88GMPTotal = f"${details.post1988GMPContractedOutDeductionsValue}%1.2f",
      revaluationRate = details.revaluationRate ,//HIP sends revaluationRate as String,changed case class to String field.
      errorCode = details.gmpErrorCode,  //HIP sends gmpErrorCode as String
      revalued = Some(if (details.revaluationCalculationSwitchIndicator) 1 else 0),
      dualCalcPost90TrueTotal = details.post1990GMPContractedOutTrueSexTotal.map(value => f"$value%1.2f"),
      dualCalcPost90OppositeTotal = details.post1990GMPContractedOutOppositeSexTotal.map(value => f"$value%1.2f"),
      inflationProofBeyondDod = Some(if (details.inflationProofBeyondDateofDeath) 1 else 0),
      contsAndEarnings = details.contributionsAndEarningsDetailsList.map(details => details.map(ContributionsAndEarningsHIP.createFromHipDetails))
    )
  }
}


case class GmpCalculationResponseHIP(
                                   name: String,
                                   nino: String,
                                   scon: String,
                                   revaluationRate: Option[String],
                                   revaluationDate: Option[LocalDate],
                                   calculationPeriods: List[CalculationPeriodHIP],
                                   globalErrorCode: String,
                                   spaDate: Option[LocalDate],
                                   payableAgeDate: Option[LocalDate],
                                   dateOfDeath: Option[LocalDate],
                                   dualCalc: Boolean,
                                   calcType: Int)

object GmpCalculationResponseHIP {
  implicit val formats: OFormat[GmpCalculationResponseHIP] = Json.format[GmpCalculationResponseHIP]

  //HIP Transformation
  def createFromHipResponse(HipCalculationResponse: HipCalculationResponse)(
    name: String,
    revaluationRate: Option[String],
    revaluationDate: Option[String],
    dualCalc: Boolean,
    calcType: Int
  ): GmpCalculationResponseHIP = {
    GmpCalculationResponseHIP(
      name = name,
      nino = HipCalculationResponse.nationalInsuranceNumber,
      scon = HipCalculationResponse.schemeContractedOutNumberDetails,
      revaluationRate = revaluationRate,
      revaluationDate = revaluationDate.map(LocalDate.parse(_)),
      calculationPeriods = HipCalculationResponse.GuaranteedMinimumPensionDetailsList.map(CalculationPeriodHIP.createFromHipGmpDetails),
      globalErrorCode = HipCalculationResponse.rejectionReason,
      spaDate = HipCalculationResponse.statePensionAgeDate.map(LocalDate.parse),
      payableAgeDate = HipCalculationResponse.payableAgeDate.map(LocalDate.parse),
      dateOfDeath = HipCalculationResponse.dateOfDeath.map(LocalDate.parse),
      dualCalc = dualCalc,
      calcType = calcType
    )
  }
}
