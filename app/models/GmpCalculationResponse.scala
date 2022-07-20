/*
 * Copyright 2022 HM Revenue & Customs
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


case class ContributionsAndEarnings(taxYear: Int, contEarnings: String)

object ContributionsAndEarnings {
  implicit val formats = Json.format[ContributionsAndEarnings]

  def createFromNpsLcntearn(earnings: NpsLcntearn): ContributionsAndEarnings = {
    ContributionsAndEarnings(earnings.rattd_tax_year, earnings.rattd_tax_year match {
      case x if x < 1987 => f"${earnings.contributions_earnings}%1.2f"
      case _ => {
        val formatter = java.text.NumberFormat.getIntegerInstance
        formatter.format(earnings.contributions_earnings)
      }
    })
  }
}

case class CalculationPeriod(startDate: Option[LocalDate],
                             endDate: LocalDate,
                             gmpTotal: String,
                             post88GMPTotal: String,
                             revaluationRate: Int,
                             errorCode: Int,
                             revalued: Option[Int],
                             dualCalcPost90TrueTotal: Option[String],
                             dualCalcPost90OppositeTotal: Option[String],
                             inflationProofBeyondDod: Option[Int],
                             contsAndEarnings: Option[List[ContributionsAndEarnings]]
                            )

object CalculationPeriod {
  implicit val formats = Json.format[CalculationPeriod]

  def createFromNpsLgmpcalc(npsLgmpcalc: NpsLgmpcalc): CalculationPeriod = {
    CalculationPeriod(npsLgmpcalc.scheme_mem_start_date.map(LocalDate.parse(_)),  LocalDate.parse(npsLgmpcalc.scheme_end_date),
      f"${npsLgmpcalc.gmp_cod_allrate_tot}%1.2f", f"${npsLgmpcalc.gmp_cod_post_eightyeight_tot}%1.2f", npsLgmpcalc.revaluation_rate, npsLgmpcalc.gmp_error_code,
      Some(npsLgmpcalc.reval_calc_switch_ind),
      npsLgmpcalc.gmp_cod_p90_ts_tot.map(value => f"$value%1.2f"),
      npsLgmpcalc.gmp_cod_p90_os_tot.map(value => f"$value%1.2f"),
      npsLgmpcalc.inflation_proof_beyond_dod,
      npsLgmpcalc.npsLcntearn.map(earnings => earnings.map(ContributionsAndEarnings.createFromNpsLcntearn))
    )
  }
}


case class GmpCalculationResponse(
                                   name: String,
                                   nino: String,
                                   scon: String,
                                   revaluationRate: Option[String],
                                   revaluationDate: Option[LocalDate],
                                   calculationPeriods: List[CalculationPeriod],
                                   globalErrorCode: Int,
                                   spaDate: Option[LocalDate],
                                   payableAgeDate: Option[LocalDate],
                                   dateOfDeath: Option[LocalDate],
                                   dualCalc: Boolean,
                                   calcType: Int) {

  def hasErrors: Boolean = calculationPeriods.foldLeft(globalErrorCode){_ + _.errorCode} > 0

  def errorCodes: List[Int] = {
    hasErrors match {
      case false => List[Int]()
      case true =>
        var errors = calculationPeriods
          .filter(_.errorCode > 0)
          .map(_.errorCode)
        if (globalErrorCode > 0)
          errors = errors :+ globalErrorCode

        errors
    }
  }

}

object GmpCalculationResponse {
  implicit val formats = Json.format[GmpCalculationResponse]

  def createFromCalculationResponse(calculationResponse: CalculationResponse)(nino: String, scon: String, name: String,
                                                                              revaluationRate: Option[Int], revaluationDate: Option[String], dualCalc: Boolean, calcType: Int):
  GmpCalculationResponse = {
    GmpCalculationResponse(name, nino, scon, revaluationRate.map(_.toString),
      revaluationDate.map(LocalDate.parse(_)),
      calculationResponse.npsLgmpcalc.map(CalculationPeriod.createFromNpsLgmpcalc),
      calculationResponse.rejection_reason,
      calculationResponse.spa_date.map(LocalDate.parse(_)),
      calculationResponse.payable_age_date.map(LocalDate.parse(_)),
      calculationResponse.dod_date.map(LocalDate.parse(_)),
      dualCalc,
      calcType
      )
  }
}
