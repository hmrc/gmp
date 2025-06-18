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

import play.api.libs.json.{Json, OFormat}

case class ContributionsAndEarningsDetails(
                        tax_year: Int,
                        contributionsOrEarningsAmount: BigDecimal
                      )

object ContributionsAndEarningsDetails{
  implicit val formats: OFormat[ContributionsAndEarningsDetails] = Json.format[ContributionsAndEarningsDetails]
}


case class GuaranteedMinimumPensionDetails(
                                            schemeMembershipStartDate: String,
                                            schemeMembershipEndDate: String,
                                            revaluationRate: String,
                                            post1988GMPContractedOutDeductionsValue: BigDecimal,
                                            gmpContractedOutDeductionsAllRateValue: BigDecimal,
                                            gmpErrorCode: String,
                                            revaluationCalculationSwitchIndicator: Boolean,
                                            post1990GMPContractedOutTrueSexTotal: BigDecimal,
                                            post1990GMPContractedOutOppositeSexTotal: BigDecimal,
                                            inflationProofBeyondDateofDeath: Boolean,
                                            contributionsAndEarningsDetailsList: Seq[ContributionsAndEarningsDetails]
                      )

object GuaranteedMinimumPensionDetails {
  implicit val formats: OFormat[GuaranteedMinimumPensionDetails] = Json.format[GuaranteedMinimumPensionDetails]
}

case class HipCalculationResponse(
                                   nationalInsuranceNumber: String,
                                   schemeContractedOutNumberDetails: String,
                                   rejectionReason: String,
                                   payableAgeDate: String,
                                   statePensionAgeDate: String,
                                   dateOfDeath: String,
                                   GuaranteedMinimumPensionDetailsList: Seq[GuaranteedMinimumPensionDetails]
                              )

object HipCalculationResponse {
  implicit val formats: OFormat[HipCalculationResponse] = Json.format[HipCalculationResponse]
}
