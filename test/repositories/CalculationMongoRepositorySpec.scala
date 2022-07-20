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

package repositories

import models.{CalculationRequest, GmpCalculationResponse}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class CalculationMongoRepositorySpec extends AnyWordSpec
  with DefaultPlayMongoRepositorySupport[CachedCalculation]
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {
  override lazy val repository = new CalculationMongoRepository(mongoComponent)

  val calculationRequest: CalculationRequest =  CalculationRequest("S2730000B", "AA000004A", "BILLING", "MARCUS", None)

  val response: GmpCalculationResponse = GmpCalculationResponse(
    name = "MARCUS BILLING",
    nino = "AA000004A",
    scon = "S2730000B",
    revaluationRate = None,
    revaluationDate = None,
    calculationPeriods = List.empty,
    globalErrorCode = 56069,
    spaDate = None,
    payableAgeDate = None,
    dateOfDeath = None,
    dualCalc = false,
    calcType = 0)

  override protected def beforeAll(): Unit =  {
    dropDatabase()
  }

  "Find By request" in {
    Await.result(repository.insertByRequest(calculationRequest, response), 1.seconds)
    val gmpCalcResposne = Await.result(repository.findByRequest(calculationRequest), 10.seconds)
    gmpCalcResposne.isDefined shouldBe true
    gmpCalcResposne.get shouldBe response
  }
}
