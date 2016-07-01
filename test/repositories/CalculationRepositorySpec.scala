/*
 * Copyright 2016 HM Revenue & Customs
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

import java.util.UUID

import helpers.mongo.MongoMocks
import models.{CalculationRequest, GmpCalculationResponse}
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import reactivemongo.api.{Collection, Cursor, CursorProducer}
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.{Awaiting, MongoSpecSupport}

import scala.concurrent.Future


class CalculationRepositorySpec extends PlaySpec with OneServerPerSuite with MongoSpecSupport with Awaiting with MockitoSugar with MongoMocks {

  class MockedCalculationRepository extends CalculationMongoRepository {
    override lazy val collection = mockCollection()
  }

  "CalculationMongoRepository" must {

    val calculationRepository = new CalculationMongoRepository

    "inserting a calculation" must {

      "persist a calculation in the repo" in {

        val repo = new MockedCalculationRepository
        val nino = s"NINO-${UUID.randomUUID()}"

        val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)
        val response = GmpCalculationResponse("f surname", nino, "SCON", None, None, List(), 0, None, None, None, false, 1)
        val cached = await(repo.insertByRequest(request, response))

        cached must be(true)
      }
    }

    "searching for a calculation" must {

      "return None when not found" in {

        val repo = new MockedCalculationRepository
        val nino = s"NINO-${UUID.randomUUID()}"

        setupFindFor(repo.collection, List[CachedCalculation]())

        val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)
        val found = await(repo.findByRequest(request))

        found must be(None)
      }

      "return response when calculation found" in {

        val repo = new MockedCalculationRepository
        val nino = s"NINO-${UUID.randomUUID()}"

        val captor = ArgumentCaptor.forClass(classOf[CachedCalculation])
        val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)
        val response = GmpCalculationResponse("f surname", nino, "SCON", None, None, List(), 0, None, None, None, false, 1)

        await(repo.insertByRequest(request, response))

        verifyInsertOn(repo.collection, captor)

        captor.getValue.request must be(request.hashCode)
        captor.getValue.response must be(response)
      }

      "return None when mongo find returns error" in {

        val mockCollection = mock[JSONCollection]
        val mockIndexesManager = mock[CollectionIndexesManager]

        when(mockCollection.indexesManager).thenReturn(mockIndexesManager)

        class TestCalculationRepository extends CalculationMongoRepository{
          override lazy val collection = mockCollection
        }

        when(mockCollection.find(Matchers.any())(Matchers.any())).thenThrow(new RuntimeException)
        when(mockCollection.indexesManager.ensure(Matchers.any())).thenReturn(Future.successful(true))

        val testRepository = new TestCalculationRepository
        val nino = s"NINO-${UUID.randomUUID()}"
        val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)

        val found = await(testRepository.findByRequest(request))

        found must be(None)
      }

    }

    // commenting out test as we don't want to run this on jenkins and can't override ttl
//    "cached calculations" must
//    {
//       //commenting out test as we don't want to run this on jenkins and can't override ttl
//          "ensure calculation response does not live longer than ttl" in {
//            val nino = s"NINO-${UUID.randomUUID()}"
//
//            val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)
//            val response = GmpCalculationResponse("f surname", nino, "SCON", None, None, List(), 0, None, None, false, 1)
//            await(calculationRepository.insertByRequest(request, response))
//
//            Thread.sleep(120000)
//
//            val found = await(calculationRepository.findByRequest(request))
//            found must be(None)
//          }
//    }
  }

  "CalculationRepository" must {

    "create a mongo repo" in {
      CalculationRepository() mustBe a[CalculationMongoRepository]
    }
  }


}
