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

import com.google.inject.Singleton
import models.{CalculationRequest, GmpCalculationResponse, HipCalculationFailuresResponse, HipCalculationResponse}

@Singleton
class HipMappingService {

  def mapSuccess(c: HipCalculationResponse, req: CalculationRequest): GmpCalculationResponse = {
    GmpCalculationResponse.createFromHipResponse(c)(
      name = s"${req.firstForename} ${req.surname}",
      revaluationRate = req.revaluationRate.map(_.toString),
      revaluationDate = req.revaluationDate,
      dualCalc = req.dualCalc.contains(1),
      calcType = req.calctype.getOrElse(-1),
      nino = req.nino,
      scon = req.scon
    )
  }

  def mapFailures(f: HipCalculationFailuresResponse, req: CalculationRequest): GmpCalculationResponse = {
    GmpCalculationResponse.createFromHipFailures(f)(
      nino = req.nino,
      scon = req.scon,
      name = s"${req.firstForename} ${req.surname}",
      revaluationRate = req.revaluationRate.map(_.toString),
      revaluationDate = req.revaluationDate,
      dualCalc = req.dualCalc.contains(1),
      calcType = req.calctype.getOrElse(-1)
    )
  }
}
