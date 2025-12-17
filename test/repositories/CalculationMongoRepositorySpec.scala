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

import models.{CalculationRequest, GmpCalculationResponse}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.PlayMongoRepositorySupport
import org.scalatest.matchers.should.Matchers
import org.mongodb.scala.SingleObservableFuture
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt

class CalculationMongoRepositorySpec extends AnyWordSpec
  with PlayMongoRepositorySupport[CachedCalculation]
  with Matchers
  with BeforeAndAfterEach
  with ScalaFutures {
  private val config         = Configuration("calculationExpiryTimeInSeconds" -> 600)
  private val servicesConfig = new ServicesConfig(config)
  override val repository: CalculationMongoRepository = new CalculationMongoRepository(mongoComponent, servicesConfig, ExecutionContext.global)

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

  val responseDiffNino = response.copy(nino = "AA000005B")
  val responseDiffScon = response.copy(scon = "S2730000C")
  val responseDiffNinoAndScon = responseDiffNino.copy(scon = "S2730000C")
  val testHashCode = calculationRequest.hashCode()

  override protected def beforeEach(): Unit =  {
    dropDatabase()
  }

  "Find By request" when {
      "there is only one record in the database with the hashCode" that {
        "matches that has the same scon and nino as the request" should {
          "return the response" in {
            Await.result(repository.insertByRequest(calculationRequest, response), 1.seconds)
            val gmpCalcResposne = Await.result(repository.findByRequest(calculationRequest), 10.seconds)
            gmpCalcResposne.isDefined shouldBe true
            gmpCalcResposne.get shouldBe response
          }
        }
        "has a different scon" should {
          "return None" in {
            val cachedCalculation = CachedCalculation(testHashCode, responseDiffScon)
            Await.result(repository.collection.insertOne(cachedCalculation).toFuture(), 1.seconds)
            val gmpCalcResposne = Await.result(repository.findByRequest(calculationRequest), 10.seconds)
            gmpCalcResposne.isDefined shouldBe false
          }
        }
        "has a different nino" should {
          "return None" in {
            val cachedCalculation = CachedCalculation(testHashCode, responseDiffNino)
            Await.result(repository.collection.insertOne(cachedCalculation).toFuture(), 1.seconds)
            val gmpCalcResposne = Await.result(repository.findByRequest(calculationRequest), 10.seconds)
            gmpCalcResposne.isDefined shouldBe false
          }
        }

        "has a different nino and scon" should {
          "return None" in {
            val cachedCalculation = CachedCalculation(testHashCode, responseDiffNinoAndScon)
            Await.result(repository.collection.insertOne(cachedCalculation).toFuture(), 1.seconds)
            val gmpCalcResposne = Await.result(repository.findByRequest(calculationRequest), 10.seconds)
            gmpCalcResposne.isDefined shouldBe false
          }
        }
      }

      "there is multiple records in the database" that {
        "includes one that matches the requests scon and nino" should {
          "return the correct response" in {
            val cachedCalculation1 = CachedCalculation(testHashCode, responseDiffScon)
            val cachedCalculation2 = CachedCalculation(testHashCode, responseDiffNino)
            val cachedCalculation3 = CachedCalculation(testHashCode, responseDiffNinoAndScon)
            val cachedCalculationMatching = CachedCalculation(testHashCode, response)
            val dataToInsert = Seq(cachedCalculation1, cachedCalculation2, cachedCalculation3, cachedCalculationMatching)

            Await.result(repository.collection.insertMany(dataToInsert).toFuture(), 1.seconds)
            val gmpCalcResposne = Await.result(repository.findByRequest(calculationRequest), 10.seconds)
            gmpCalcResposne.isDefined shouldBe true
            gmpCalcResposne.get shouldBe response
        }
      }

        "does not include one that matches the requests scon and nino" should {
          "return None" in {
            val cachedCalculation1 = CachedCalculation(testHashCode, responseDiffScon)
            val cachedCalculation2 = CachedCalculation(testHashCode, responseDiffNino)
            val cachedCalculation3 = CachedCalculation(testHashCode, responseDiffNinoAndScon)
            val dataToInsert = Seq(cachedCalculation1, cachedCalculation2, cachedCalculation3)

            Await.result(repository.collection.insertMany(dataToInsert).toFuture(), 1.seconds)
            val gmpCalcResposne = Await.result(repository.findByRequest(calculationRequest), 10.seconds)
            gmpCalcResposne.isDefined shouldBe false
          }
        }

        "deserialize createdAt as Mongo Extended JSON with $date.$numberLong (UTC millis)" in {
          val expectedTime = Instant.parse("2025-11-03T10:00:00Z")
          val dt = expectedTime.toEpochMilli
          
          val json = Json.parse(
            s"""
              |{
              |"request": 69942156,
              |  "response": {
              |    "name": "CLIFF RICHARD-SMITH",
              |    "nino": "WA106701A",
              |    "scon": "S2730000B",
              |    "calculationPeriods": [
              |      {
              |        "startDate": "1996-04-06",
              |        "endDate": "2016-04-05",
              |        "gmpTotal": "0.71",
              |        "post88GMPTotal": "0.71",
              |        "revaluationRate": 1,
              |        "errorCode": 0,
              |        "revalued": 0,
              |        "dualCalcPost90TrueTotal": "0.00",
              |        "dualCalcPost90OppositeTotal": "0.00",
              |        "inflationProofBeyondDod": 0,
              |        "contsAndEarnings": [
              |          {
              |            "taxYear": 1996,
              |            "contEarnings": "4,785"
              |          }
              |        ]
              |      }
              |    ],
              |    "globalErrorCode": 0,
              |    "payableAgeDate": "2028-03-02",
              |    "dualCalc": false,
              |    "calcType": 0
              |  },
              |  "createdAt": { "$$date": { "$$numberLong": "$dt" } }
              |}
           """.stripMargin)

          val details = json.as[CachedCalculation]

          details.createdAt  shouldBe Instant.parse("2025-11-03T10:00:00Z")

        }

        "serialize createdAt from Mongo Extended JSON structure" in {
          val localDateTime = LocalDateTime.of(2024, 6, 30, 12, 34, 56, 789000000)
          val millisTime = localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli
          val instant = Instant.ofEpochMilli(millisTime)

          val json = Json.obj(
            "request" -> testHashCode,
            "response" -> response,
            "createdAt" -> Json.obj(
              "$date" -> Json.obj("$numberLong" -> millisTime.toString)
            )
          )

          val parsed = json.validate[CachedCalculation]
          parsed.isSuccess shouldBe true
          parsed.get shouldBe CachedCalculation(testHashCode, response, instant)
        }
    }
  }
}
