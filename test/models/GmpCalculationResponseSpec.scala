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

import base.BaseSpec
import helpers.RandomNino

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import play.api.libs.json.Json

class GmpCalculationResponseSpec extends BaseSpec {

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

  "GmpCalculationResponse.createFromCalculationResponse" should {

    "Correctly transform a full HIP response with all fields" in{

      val hipJson = Json.parse(
        s"""{
          "nationalInsuranceNumber": "AA000001A",
          "schemeContractedOutNumberDetails": "S2123456B",
          "rejectionReason": "No match for person details provided",
          "payableAgeDate": "2022-06-27",
          "statePensionAgeDate": "2022-06-27",
          "dateOfDeath": "2022-06-27",
          "GuaranteedMinimumPensionDetailsList": [
          {
          "schemeMembershipStartDate": "2022-06-27",
          "schemeMembershipEndDate": "2022-06-27",
          "revaluationRate": "FIXED",
          "post1988GMPContractedOutDeductionsValue": 10.56,
          "gmpContractedOutDeductionsAllRateValue": 10.56,
          "gmpErrorCode": "Input revaluation date is before the termination date held on hmrc records",
          "revaluationCalculationSwitchIndicator": true,
          "post1990GMPContractedOutTrueSexTotal": 10.56,
          "post1990GMPContractedOutOppositeSexTotal": 10.56,
          "inflationProofBeyondDateofDeath": true,
          "contributionsAndEarningsDetailsList": [{
                                                 "taxYear": 2000,
                                                 "contributionOrEarningsAmount": 1560
                                                 }]
          }
          ]
          }""").as[HipCalculationResponse]


      val gmpResponse = GmpCalculationResponse.createFromHipResponse(hipJson)("John Johnson",
        Some("FIXED"), None, true, 0)

      gmpResponse.nino must be ("AA000001A")
      gmpResponse.scon must be ("S2123456B")
      gmpResponse.name must include ("John")
      gmpResponse.spaDate must be (Some(LocalDate.parse("2022-06-27", fullDateFormatter)))
      gmpResponse.revaluationRate mustBe Some("FIXED")
      gmpResponse.calculationPeriods.head.gmpTotal must be("10.56")
      gmpResponse.calculationPeriods.head.contsAndEarnings.get.head.contEarnings must be("1,560")
      gmpResponse.calculationPeriods.head.contsAndEarnings.get.head.taxYear must be(2000)
    }

    "handle an empty GuaranteedMinimumPensionDetailsList gracefully" in {
      val hipJson =
        """
          {
            "nationalInsuranceNumber": "",
            "schemeContractedOutNumberDetails": "",
            "rejectionReason": "Some error",
            "GuaranteedMinimumPensionDetailsList": []
          }
        """
      val hipResponse = Json.parse(hipJson).as[HipCalculationResponse]
      val result = GmpCalculationResponse.createFromHipResponse(hipResponse)("John Johnson",
        None, None, true, 0)

      result.calculationPeriods mustBe empty
    }

    "handle an empty contributionsAndEarningsDetailsList gracefully" in{
      val hipJson =
        """{
          "nationalInsuranceNumber": "AA000001A",
          "schemeContractedOutNumberDetails": "S2123456B",
          "rejectionReason": "No match for person details provided",
          "payableAgeDate": "2022-06-27",
          "statePensionAgeDate": "2022-06-27",
          "dateOfDeath": "2022-06-27",
          "GuaranteedMinimumPensionDetailsList": [
          {
          "schemeMembershipStartDate": "2022-06-27",
          "schemeMembershipEndDate": "2022-06-27",
          "revaluationRate": "(NONE)",
          "post1988GMPContractedOutDeductionsValue": 10.56,
          "gmpContractedOutDeductionsAllRateValue": 10.56,
          "gmpErrorCode": "Input revaluation date is before the termination date held on hmrc records",
          "revaluationCalculationSwitchIndicator": true,
          "post1990GMPContractedOutTrueSexTotal": 10.56,
          "post1990GMPContractedOutOppositeSexTotal": 10.56,
          "inflationProofBeyondDateofDeath": true,
          "contributionsAndEarningsDetailsList": []
          }
          ]
          }
          """

      val hipResponse =Json.parse(hipJson).as[HipCalculationResponse]

      val gmpResponse = GmpCalculationResponse.createFromHipResponse(hipResponse)("John Johnson",
        None, None, true, 0)

      gmpResponse.calculationPeriods.head.contsAndEarnings.head mustBe empty

    }

    "handle an empty GuaranteedMinimumPensionDetailsList and rejectionReason gracefully" in {
      val hipJson =
        """
          {
            "nationalInsuranceNumber": "",
            "schemeContractedOutNumberDetails": "",
            "rejectionReason": "",
            "GuaranteedMinimumPensionDetailsList": []
          }
        """
      val hipResponse = Json.parse(hipJson).as[HipCalculationResponse]
      val result = GmpCalculationResponse.createFromHipResponse(hipResponse)("John Johnson",
        None, None, true, 0)

      result.globalErrorCode mustBe 0
    }
    }

  "createFromHipGmpDetails " should {
    "Correctly map revaluationRate string enum to expected int values" in {

      val details = GuaranteedMinimumPensionDetails(
        schemeMembershipStartDate = Some("2020-01-01"),
        schemeMembershipEndDate = "2024-01-01",
        gmpContractedOutDeductionsAllRateValue = BigDecimal("123.45"),
        post1988GMPContractedOutDeductionsValue = BigDecimal("67.89"),
        revaluationRate = "FIXED",
        gmpErrorCode = "Input revaluation date is before the termination date held on hmrc records",
        revaluationCalculationSwitchIndicator = true,
        post1990GMPContractedOutTrueSexTotal = Some(BigDecimal("45.67")),
        post1990GMPContractedOutOppositeSexTotal = Some(BigDecimal("23.45")),
        inflationProofBeyondDateofDeath = false,
        contributionsAndEarningsDetailsList = None
      )

      val result = CalculationPeriod.createFromHipGmpDetails(details)
      result.revaluationRate mustBe 2

    }

    "default to 0 if revaluationRate is unknown" in {

      val details = GuaranteedMinimumPensionDetails(
        schemeMembershipStartDate = Some("2020-01-01"),
        schemeMembershipEndDate = "2024-01-01",
        gmpContractedOutDeductionsAllRateValue = BigDecimal("123.45"),
        post1988GMPContractedOutDeductionsValue = BigDecimal("67.89"),
        revaluationRate = "UNKNOWN",
        gmpErrorCode = "Input revaluation date is before the termination date held on hmrc records",
        revaluationCalculationSwitchIndicator = true,
        post1990GMPContractedOutTrueSexTotal = Some(BigDecimal("45.67")),
        post1990GMPContractedOutOppositeSexTotal = Some(BigDecimal("23.45")),
        inflationProofBeyondDateofDeath = false,
        contributionsAndEarningsDetailsList = None
      )

      val result = CalculationPeriod.createFromHipGmpDetails(details)
      result.revaluationRate mustBe 0

    }

    "Correctly map revaluationRate (NONE) to expected 0 int value" in {

      val details = GuaranteedMinimumPensionDetails(
        schemeMembershipStartDate = Some("2020-01-01"),
        schemeMembershipEndDate = "2024-01-01",
        gmpContractedOutDeductionsAllRateValue = BigDecimal("123.45"),
        post1988GMPContractedOutDeductionsValue = BigDecimal("67.89"),
        revaluationRate = "(NONE)",
        gmpErrorCode = "Input revaluation date is before the termination date held on hmrc records",
        revaluationCalculationSwitchIndicator = true,
        post1990GMPContractedOutTrueSexTotal = Some(BigDecimal("45.67")),
        post1990GMPContractedOutOppositeSexTotal = Some(BigDecimal("23.45")),
        inflationProofBeyondDateofDeath = false,
        contributionsAndEarningsDetailsList = None
      )

      val result = CalculationPeriod.createFromHipGmpDetails(details)
      result.revaluationRate mustBe 0

    }
    "Correctly map revaluationRate S148 to expected 1 int value" in {

      val details = GuaranteedMinimumPensionDetails(
        schemeMembershipStartDate = Some("2020-01-01"),
        schemeMembershipEndDate = "2024-01-01",
        gmpContractedOutDeductionsAllRateValue = BigDecimal("123.45"),
        post1988GMPContractedOutDeductionsValue = BigDecimal("67.89"),
        revaluationRate = "S148",
        gmpErrorCode = "Input revaluation date is before the termination date held on hmrc records",
        revaluationCalculationSwitchIndicator = true,
        post1990GMPContractedOutTrueSexTotal = Some(BigDecimal("45.67")),
        post1990GMPContractedOutOppositeSexTotal = Some(BigDecimal("23.45")),
        inflationProofBeyondDateofDeath = false,
        contributionsAndEarningsDetailsList = None
      )

      val result = CalculationPeriod.createFromHipGmpDetails(details)
      result.revaluationRate mustBe 1

    }
    "Correctly map revaluationRate LIMITED to expected 3 int value" in {

      val details = GuaranteedMinimumPensionDetails(
        schemeMembershipStartDate = Some("2020-01-01"),
        schemeMembershipEndDate = "2024-01-01",
        gmpContractedOutDeductionsAllRateValue = BigDecimal("123.45"),
        post1988GMPContractedOutDeductionsValue = BigDecimal("67.89"),
        revaluationRate = "LIMITED",
        gmpErrorCode = "Input revaluation date is before the termination date held on hmrc records",
        revaluationCalculationSwitchIndicator = true,
        post1990GMPContractedOutTrueSexTotal = Some(BigDecimal("45.67")),
        post1990GMPContractedOutOppositeSexTotal = Some(BigDecimal("23.45")),
        inflationProofBeyondDateofDeath = false,
        contributionsAndEarningsDetailsList = None
      )

      val result = CalculationPeriod.createFromHipGmpDetails(details)
      result.revaluationRate mustBe 3

    }
  }
}
