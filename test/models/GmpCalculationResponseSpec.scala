/*
 * Copyright 2016 HM Revenue & Customs
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
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

class GmpCalculationResponseSpec extends PlaySpec {

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
              "scheme_end_date": "200-04-05",
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
      gmpResponse.dateOfDeath must be(Some(new LocalDate("2016-01-01")))
      gmpResponse.dualCalc must be(true)
      gmpResponse.calcType must be(1)
    }


    "has errors" must {
      "return true when global error" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", None, None, Nil, 56010, None, None, None, false, 1)
        response.hasErrors must be(true)
      }

      "return false when no cop errorsr" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", Some("1"), Some(new LocalDate(2000, 11, 11)),
          List(CalculationPeriod(Some(new LocalDate(2015, 11, 10)), new LocalDate(2015, 11, 10), "1.11", "2.22", 1, 0, Some(1), None, None, None, None),
               CalculationPeriod(Some(new LocalDate(2015, 11, 10)), new LocalDate(2015, 11, 10), "1.11", "2.22", 1, 0, Some(1), None, None, None, None)), 0, None, None, None, false, 1)
        response.hasErrors must be(false)
      }

      "return true when one cop error" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", Some("1"), Some(new LocalDate(2000, 11, 11)),
          List(CalculationPeriod(Some(new LocalDate(2015, 11, 10)), new LocalDate(2015, 11, 10), "1.11", "2.22", 1, 0, Some(1), None, None, None, None),
               CalculationPeriod(Some(new LocalDate(2015, 11, 10)), new LocalDate(2015, 11, 10), "1.11", "2.22", 1, 6666, None, None, None, None, None)), 0, None, None, None, false, 1)
        response.hasErrors must be(true)
      }

      "return true when multi cop error" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", None, Some(new LocalDate(2000, 11, 11)),
          List(CalculationPeriod(Some(new LocalDate(2015, 11, 10)), new LocalDate(2015, 11, 10), "0.00", "0.00", 0, 56023, None, None, None, None, None),
               CalculationPeriod(Some(new LocalDate(2010, 11, 10)), new LocalDate(2011, 11, 10), "0.00", "0.00", 0, 56007, None, None, None, None, None)), 0, None, None, None, false, 1)
        response.hasErrors must be(true)
      }
    }

    "errorCodes" must {
      "return an empty list when no error codes" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", Some("1"), Some(new LocalDate(2000, 11, 11)),
          List(CalculationPeriod(Some(new LocalDate(2012, 1, 1)), new LocalDate(2015, 1, 1), "1.11", "2.22", 1, 0, Some(1), None, None, None, None)), 0, None, None, None, false, 1)
        response.errorCodes.size must be(0)
      }

      "return a list of error codes with global error code" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", Some("1"), Some(new LocalDate(2000, 11, 11)),
          List(CalculationPeriod(Some(new LocalDate(2015, 11, 10)),new LocalDate(2015, 11, 10), "0.00", "0.00", 0, 0, None, None, None, None, None)), 48160, None, None, None, false, 1)
        response.errorCodes.size must be(1)
        response.errorCodes.head must be(48160)
      }

      "return a list of error codes with period error codes" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", None, Some(new LocalDate(2000, 11, 11)),
          List(CalculationPeriod(Some(new LocalDate(2015, 11, 10)),new LocalDate(2015, 11, 10), "0.00", "0.00", 0, 56023, None, None, None, None, None),
               CalculationPeriod(Some(new LocalDate(2010, 11, 10)),new LocalDate(2011, 11, 10), "0.00", "0.00", 0, 56007, None, None, None, None, None),
               CalculationPeriod(Some(new LocalDate(2010, 11, 10)),new LocalDate(2011, 11, 10), "0.00", "0.00", 0, 0, None, None, None, None, None)), 0, None, None, None, false, 1)
        response.errorCodes.size must be(2)
        response.errorCodes must be(List(56023, 56007))
      }

      "return a list of error codes with period error codes and global error code" in {
        val response = GmpCalculationResponse("John Johnson", nino, "S1234567T", None, Some(new LocalDate(2000, 11, 11)),
          List(CalculationPeriod(Some(new LocalDate(2015, 11, 10)),new LocalDate(2015, 11, 10), "0.00", "0.00", 0, 56023, None, None, None, None, None),
               CalculationPeriod(Some(new LocalDate(2010, 11, 10)),new LocalDate(2011, 11, 10), "0.00", "0.00", 0, 56007, None, None, None, None, None),
               CalculationPeriod(Some(new LocalDate(2010, 11, 10)),new LocalDate(2011, 11, 10), "0.00", "0.00", 0, 0, None, None, None, None, None)), 48160, None, None, None, false, 1)
        response.errorCodes.size must be(3)
        response.errorCodes must be(List(56023, 56007, 48160))
      }
    }
  }

}
