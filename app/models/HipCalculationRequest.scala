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

import play.api.libs.json.{Json, OFormat}
import models.CalculationRequest

case class HipCalculationRequest(schemeContractedOutNumber: String,
                                 nationalInsuranceNumber: String,
                                 surname: String,
                                 firstForename: String,
                                 secondForename: Option[String],
                                 revaluationRate: String,
                                 calculationRequestType: String,
                                 revaluationDate: String,
                                 terminationDate: String,
                                 includeContributionAndEarnings: Boolean,
                                 includeDualCalculation: Boolean)

object HipCalculationRequest {
  implicit val formats: OFormat[HipCalculationRequest] = Json.format[HipCalculationRequest]

  def from (calcReq: CalculationRequest): HipCalculationRequest = {
    HipCalculationRequest(
      schemeContractedOutNumber= calcReq.scon,
      nationalInsuranceNumber= calcReq.nino,
      surname=calcReq.surname,
      firstForename=calcReq.firstForename,
      secondForename=None,
      revaluationRate=calcReq.revaluationRate.map(_.toString).getOrElse("(NONE)"),
      calculationRequestType="", // update later
      revaluationDate= calcReq.revaluationDate.getOrElse("(NONE)"),
      terminationDate=calcReq.terminationDate.getOrElse("(NONE)"),
      includeContributionAndEarnings= calcReq.requestEarnings.contains(1),
      includeDualCalculation= calcReq.dualCalc.contains(1)
    )
  }
}

