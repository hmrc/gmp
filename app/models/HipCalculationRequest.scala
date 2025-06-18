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

