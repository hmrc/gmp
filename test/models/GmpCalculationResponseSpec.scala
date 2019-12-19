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

package models

import helpers.RandomNino
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

class GmpCalculationResponseSpec extends PlaySpec {

  private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val inputDate1 = LocalDate.parse("2015-11-10", fullDateFormatter)
  val inputDate2 = LocalDate.parse("2000-11-11", fullDateFormatter)
  val inputDate3 = LocalDate.parse("2010-11-10", fullDateFormatter)
  val inputDate4 = LocalDate.parse("2011-11-10", fullDateFormatter)
  val inputDate5 = LocalDate.parse("2012-01-01", fullDateFormatter)

  def nino = RandomNino.generate

  "createFromNpsLgmpcalc" must {
    "correctly format currency amounts" in {

      val serverResponse = Json.parse(
        s"""{
              "nino": "$nino",
              "rejection_reason": 0,
              "dod_date":"2016-01-01",
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.2,
              "gmp_cod_allrate_tot": 1,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0,
              "npsLcntearn" : [
              {
                "rattd_tax_year": 1986,
                "contributions_earnings": 239.8
              },
              {
                "rattd_tax_year": 1987,
                "contributions_earnings": 1560
              }
              ]
              }
              ]
              }""").as[CalculationResponse]

      val gmpResponse = GmpCalculationResponse.createFromCalculationResponse(serverResponse)(nino, "", "",
        None, None, true, 1)

      gmpResponse.calculationPeriods.head.post88GMPTotal must be("1.20")
      gmpResponse.calculationPeriods.head.gmpTotal must be("1.00")
      gmpResponse.calculationPeriods.head.contsAndEarnings.get.head.contEarnings must be("239.80")
      gmpResponse.calculationPeriods.head.contsAndEarnings.get.tail.head.contEarnings must be("1,560")
      gmpResponse.dateOfDeath must be(Some(LocalDate.parse("2016-01-01", fullDateFormatter)))
      gmpResponse.dualCalc must be(true)
      gmpResponse.calcType must be(1)
    }


    "has errors" must {
      "return true when global error" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", None, None, Nil, 56010, None, None, None, false, 1)
        response.hasErrors must be(true)
      }

      "return false when no cop errorsr" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", Some("1"), Some(inputDate2),
          List(CalculationPeriod(Some(inputDate1), inputDate1, "1.11", "2.22", 1, 0, Some(1), None, None, None, None),
               CalculationPeriod(Some(inputDate1), inputDate1, "1.11", "2.22", 1, 0, Some(1), None, None, None, None)), 0, None, None, None, false, 1)
        response.hasErrors must be(false)
      }

      "return true when one cop error" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", Some("1"), Some(inputDate2),
          List(CalculationPeriod(Some(inputDate1), inputDate1, "1.11", "2.22", 1, 0, Some(1), None, None, None, None),
               CalculationPeriod(Some(inputDate1), inputDate1, "1.11", "2.22", 1, 6666, None, None, None, None, None)), 0, None, None, None, false, 1)
        response.hasErrors must be(true)
      }

      "return true when multi cop error" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", None, Some(inputDate2),
          List(CalculationPeriod(Some(inputDate1), inputDate1, "0.00", "0.00", 0, 56023, None, None, None, None, None),
               CalculationPeriod(Some(inputDate1), inputDate1, "0.00", "0.00", 0, 56007, None, None, None, None, None)), 0, None, None, None, false, 1)
        response.hasErrors must be(true)
      }
    }

    "errorCodes" must {
      "return an empty list when no error codes" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", Some("1"), Some(inputDate2),
          List(CalculationPeriod(Some(inputDate5), inputDate1, "1.11", "2.22", 1, 0, Some(1), None, None, None, None)), 0, None, None, None, false, 1)
        response.errorCodes.size must be(0)
      }

      "return a list of error codes with global error code" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", Some("1"), Some(inputDate2),
          List(CalculationPeriod(Some(inputDate1),inputDate1, "0.00", "0.00", 0, 0, None, None, None, None, None)), 48160, None, None, None, false, 1)
        response.errorCodes.size must be(1)
        response.errorCodes.head must be(48160)
      }

      "return a list of error codes with period error codes" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", None, Some(inputDate2),
          List(CalculationPeriod(Some(inputDate1),inputDate1, "0.00", "0.00", 0, 56023, None, None, None, None, None),
               CalculationPeriod(Some(inputDate1),inputDate1, "0.00", "0.00", 0, 56007, None, None, None, None, None),
               CalculationPeriod(Some(inputDate1),inputDate1, "0.00", "0.00", 0, 0, None, None, None, None, None)), 0, None, None, None, false, 1)
        response.errorCodes.size must be(2)
        response.errorCodes must be(List(56023, 56007))
      }

      "return a list of error codes with period error codes and global error code" in {

        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", None, Some(inputDate2),
          List(CalculationPeriod(Some(inputDate1),inputDate1, "0.00", "0.00", 0, 56023, None, None, None, None, None),
               CalculationPeriod(Some(inputDate1),inputDate1, "0.00", "0.00", 0, 56007, None, None, None, None, None),
               CalculationPeriod(Some(inputDate3),inputDate4, "0.00", "0.00", 0, 0, None, None, None, None, None)), 48160, None, None, None, false, 1)
        response.errorCodes.size must be(3)
        response.errorCodes must be(List(56023, 56007, 48160))
      }
    }
  }

}
