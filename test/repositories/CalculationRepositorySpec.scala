/*
 * Copyright 2019 HM Revenue & Customs
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

/*
 * Commenting out spec.  Update to Mongo means that Mockito class is final and can't therefore mock.
 * There is no value in this test at a unit level as it is only testing the db calls and NOT the service.
 */

//
//package repositories
//
//import java.util.UUID
//
//import helpers.mongo.MongoMocks
//import models.{CalculationRequest, GmpCalculationResponse}
//import org.mockito.Mockito._
//import org.mockito.{ArgumentCaptor, Matchers}
//import org.scalatest.mock.MockitoSugar
//import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
//import play.api.inject.guice.GuiceApplicationBuilder
//import uk.gov.hmrc.mongo.Awaiting
//
//
//class CalculationRepositorySpec extends PlaySpec with OneAppPerSuite with Awaiting with MockitoSugar with MongoMocks {
//
//  implicit lazy override val app = new GuiceApplicationBuilder().build()
//
//  class MockedCalculationRepository extends CalculationMongoRepository()(() => mockMongoDb) {
//    override lazy val collection = mockCollection()
//  }
//
//  "CalculationMongoRepository" must {
//
//    "inserting a calculation" must {
//
//      "persist a calculation in the repo" in {
//
//        val repo = new MockedCalculationRepository
//        val nino = s"NINO-${UUID.randomUUID()}"
//        val captor = ArgumentCaptor.forClass(classOf[CachedCalculation])
//        val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)
//        val response = GmpCalculationResponse("f surname", nino, "SCON", None, None, List(), 0, None, None, None, false, 1)
//
//        val cached = await(repo.insertByRequest(request, response))
//
//        verifyInsertOn(repo.collection, captor)
//
//        captor.getValue.request must be(request.hashCode)
//        captor.getValue.response must be(response)
//        cached must be(true)
//      }
//    }
//
//    "search for a calculation" must {
//
//      "return None when not found" in {
//
//        val repo = new MockedCalculationRepository
//        val nino = s"NINO-${UUID.randomUUID()}"
//
//        setupFindFor(repo.collection, List[CachedCalculation]())
//
//        val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)
//        val found = await(repo.findByRequest(request))
//
//        found must be(None)
//      }
//
//      "return response when calculation found" in {
//
//        val repo = new MockedCalculationRepository
//
//        val request = mock[CalculationRequest]
//        val response = mock[GmpCalculationResponse]
//        val cachedCalc = mock[CachedCalculation]
//
//        when(cachedCalc.response) thenReturn response
//
//        setupFindFor(repo.collection, List(cachedCalc))
//
//        val result = await(repo.findByRequest(request))
//
//        result must be(defined)
//        result.get must be(response)
//      }
//
//      "return None when mongo find returns error" in {
//
//        val repo = new MockedCalculationRepository
//
//        when(repo.collection.find(Matchers.any())(Matchers.any())).thenThrow(new RuntimeException)
//
//        val nino = s"NINO-${UUID.randomUUID()}"
//        val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)
//
//        val found = await(repo.findByRequest(request))
//
//        found must be(None)
//      }
//
//    }
//
//    // commenting out test as we don't want to run this on jenkins and can't override ttl
////    "cached calculations" must
////    {
////       //commenting out test as we don't want to run this on jenkins and can't override ttl
////          "ensure calculation response does not live longer than ttl" in {
////            val nino = s"NINO-${UUID.randomUUID()}"
////
////            val request = CalculationRequest("SCON", nino, "surname", "f", None, None, None)
////            val response = GmpCalculationResponse("f surname", nino, "SCON", None, None, List(), 0, None, None, false, 1)
////            await(calculationRepository.insertByRequest(request, response))
////
////            Thread.sleep(120000)
////
////            val found = await(calculationRepository.findByRequest(request))
////            found must be(None)
////          }
////    }
//  }
//
//  "CalculationRepository" must {
//
//    "create a mongo repo" in {
//      CalculationRepository() mustBe a[CalculationMongoRepository]
//    }
//  }
//
//
//}
