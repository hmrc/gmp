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

package repositories

import models.GmpValidateSconResponse
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.PlayMongoRepositorySupport
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt

class ValidateSconRepositorySpec extends AnyWordSpec
  with PlayMongoRepositorySupport[ValidateSconMongoModel]
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {
  private val config = Configuration("sconValidationExpiryTimeInSeconds" -> 600)
  private val servicesConfig = new ServicesConfig(config)
  override val repository: ValidateSconMongoRepository = new ValidateSconMongoRepository(mongoComponent,servicesConfig,ExecutionContext.global)

  override protected def beforeAll(): Unit =  {
    dropDatabase()
  }

  private val scon1 = "S2730000B"
  private val response1 = GmpValidateSconResponse(true)

  private val scon2 = "S2730000B"
  private val response2 = GmpValidateSconResponse(false)


  "Find By scon" in {
    Await.result(repository.insertByScon(scon2, response2), 1.seconds)
    Await.result(repository.insertByScon(scon1, response1), 1.seconds)
    val result = Await.result(repository.findByScon(scon1), 2.seconds)
    result.isDefined shouldBe true
    result.get shouldBe response2
  }


}
