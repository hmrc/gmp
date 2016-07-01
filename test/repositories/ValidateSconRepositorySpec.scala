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

import controllers.CalculationController
import helpers.mongo.MongoMocks
import models.{CalculationRequest, GmpValidateSconResponse, ValidateSconResponse}
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar

import org.scalatestplus.play.{PlaySpec, OneServerPerSuite}
import play.api.libs.json.Json
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.{Awaiting, MongoSpecSupport}

import scala.concurrent.Future

class ValidateSconRepositorySpec extends PlaySpec
  with OneServerPerSuite
  with MongoSpecSupport
  with Awaiting
  with MockitoSugar
  with MongoMocks
  with BeforeAndAfter {

  class MockedSconRepository extends ValidateSconMongoRepository {
    override lazy val collection = mockCollection()
  }

  val repository = new MockedSconRepository()

  before {
    reset(repository.collection)
  }

  "ValidateSconMongoRepository" must {

    "return None when scon not found" in {
      val scon = UUID.randomUUID().toString
      val response = GmpValidateSconResponse(true)

      setupFindFor(repository.collection, Json.obj("scon" -> scon), List())

      val found = await(repository.findByScon(scon))

      found must be(None)
    }

    "successfully save a validate scon response" in {

      val scon = UUID.randomUUID().toString
      val response = GmpValidateSconResponse(true)
      val captor = ArgumentCaptor.forClass(classOf[ValidateSconMongoModel])

      setupAnyInsertOn(repository.collection, fails = false)

      val created = await(repository.insertByScon(scon, response))

      created must be(true)

      verifyInsertOn(repository.collection, captor)

      captor.getValue.scon must be(scon)
      captor.getValue.response must be(response)
    }

    "return None when mongo find returns error" in {

      val mockCollection = mock[JSONCollection]
      val mockIndexesManager = mock[CollectionIndexesManager]

      when(mockCollection.indexesManager).thenReturn(mockIndexesManager)
      when(mockCollection.find(Matchers.any())(Matchers.any())).thenThrow(new RuntimeException)
      when(mockCollection.indexesManager.ensure(Matchers.any())).thenReturn(Future.successful(true))

      val scon = UUID.randomUUID().toString

      val found = await(repository.findByScon(scon))
      found must be(None)
    }

    // commenting out test as we don't want to run this on jenkins and can't override ttl
//    "ensure scon response does not live longer than ttl" in {
//      val scon = UUID.randomUUID().toString
//      val response = GmpValidateSconResponse(true)
//      val created = await(repository.insertByScon(scon, response))
//      created must be(true)
//      Thread.sleep(120000)
//      val found = await(repository.findByScon(scon))
//      found must be(None)
//    }
  }

  "ValidateSconRepository" must {

    "create a mongo repo" in {
      ValidateSconRepository() mustBe a[ValidateSconMongoRepository]
    }
  }

}