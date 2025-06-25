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

class GmpCalculationResponseHIPSpec extends BaseSpec {

  private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  "GmpCalculationResponseHIP.createFromHipResponse" should {

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
          "revaluationRate": "(NONE)",
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


      val gmpResponse = GmpCalculationResponseHIP.createFromHipResponse(hipJson)("John Johnson",
        None, None, true, 0)

      gmpResponse.nino must be ("AA000001A")
      gmpResponse.scon must be ("S2123456B")
      gmpResponse.name must include ("John")
      gmpResponse.spaDate must be (Some(LocalDate.parse("2022-06-27", fullDateFormatter)))
      gmpResponse.revaluationRate must be (None)
      gmpResponse.globalErrorCode must be ("No match for person details provided")
      gmpResponse.calculationPeriods.head.gmpTotal must be("10.56")
      gmpResponse.calculationPeriods.head.errorCode must be ("Input revaluation date is before the termination date held on hmrc records")
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
      val result = GmpCalculationResponseHIP.createFromHipResponse(hipResponse)("John Johnson",
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

      val gmpResponse = GmpCalculationResponseHIP.createFromHipResponse(hipResponse)("John Johnson",
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
      val result = GmpCalculationResponseHIP.createFromHipResponse(hipResponse)("John Johnson",
        None, None, true, 0)

      result.globalErrorCode mustBe ""
    }
  }
}
