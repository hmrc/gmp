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

package services

import base.BaseSpec
import models._
import play.api.libs.json.Json

class HipMappingServiceSpec extends BaseSpec {

  private val service = new HipMappingService()

  private val request = CalculationRequest(
    scon = "S2123456B",
    nino = "AA000001A",
    surname = "Smith",
    firstForename = "Bill",
    revaluationRate = Some(1),
    revaluationDate = Some("2022-06-27"),
    calctype = Some(1),
    dualCalc = Some(1)
  )

  "HipMappingService.mapSuccess" should {
    "map HIP success response to GMP correctly" in {
      val hipJson = Json.parse(
        """{
          "nationalInsuranceNumber": "AA000001A",
          "schemeContractedOutNumberDetails": "S2123456B",
          "payableAgeDate": "2022-06-27",
          "statePensionAgeDate": "2022-06-27",
          "dateOfDeath": "2022-06-27",
          "GuaranteedMinimumPensionDetailsList": [
            {
              "schemeMembershipStartDate": "1978-04-06",
              "schemeMembershipEndDate": "2006-04-05",
              "revaluationRate": "FIXED",
              "post1988GMPContractedOutDeductionsValue": 1.23,
              "gmpContractedOutDeductionsAllRateValue": 10.56,
              "gmpErrorCode": "Input revaluation date is before the termination date held on hmrc records",
              "revaluationCalculationSwitchIndicator": true,
              "post1990GMPContractedOutTrueSexTotal": 10.56,
              "post1990GMPContractedOutOppositeSexTotal": 10.56,
              "inflationProofBeyondDateofDeath": true,
              "contributionsAndEarningsDetailsList": []
            }
          ]
        }"""
      ).as[HipCalculationResponse]

      val result = service.mapSuccess(hipJson, request)

      result.nino mustBe request.nino
      result.scon mustBe request.scon
      result.name mustBe s"${request.firstForename} ${request.surname}"
      result.revaluationRate mustBe Some(request.revaluationRate.get.toString)
      result.revaluationDate.map(_.toString) mustBe request.revaluationDate
      result.globalErrorCode mustBe 0
      result.spaDate.map(_.toString) mustBe Some("2022-06-27")
      result.payableAgeDate.map(_.toString) mustBe Some("2022-06-27")
      result.dualCalc mustBe true
      result.calcType mustBe 1
      result.calculationPeriods.head.gmpTotal mustBe "10.56"
      result.calculationPeriods.head.post88GMPTotal mustBe "1.23"
    }
  }

  "HipMappingService.mapFailures" should {
    "map HIP failures (422) to GMP with error code and empty periods" in {
      val failures = HipCalculationFailuresResponse(failures = List(HipFailure("No match", Some("63119"), None)))
      val result = service.mapFailures(failures, 422, request)

      result.globalErrorCode mustBe 63119
      result.calculationPeriods mustBe empty
      result.scon mustBe request.scon
      result.nino mustBe request.nino
    }

    "use HTTP status as global error code when HIP failure has no code but has a type" in {
      val failures = HipCalculationFailuresResponse(failures = List(HipFailure("Integration unavailable", None, Some("HIP-UNAVAILABLE"))))
      val result = service.mapFailures(failures, 503, request)

      result.globalErrorCode mustBe 503
      result.calculationPeriods mustBe empty
    }
  }
}
