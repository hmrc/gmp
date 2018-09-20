/*
 * Copyright 2018 HM Revenue & Customs
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
import org.joda.time.{DateTimeZone, DateTime}
import play.api.Logger
import play.api.libs.json.Json
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.api.{ReadPreference, DefaultDB}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure, Try}

case class CachedCalculation(request: Int,
                             response: GmpCalculationResponse,
                             createdAt: DateTime = DateTime.now(DateTimeZone.UTC))

object CachedCalculation {

  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat = ReactiveMongoFormats.objectIdFormats
  implicit val formats = Json.format[CachedCalculation]

}

trait CalculationRepository extends Repository[CachedCalculation, BSONObjectID] {

  def findByRequest(request: CalculationRequest): Future[Option[GmpCalculationResponse]]

  def insertByRequest(request: CalculationRequest, response: GmpCalculationResponse): Future[Boolean]

}

class CalculationMongoRepository()(implicit mongo: () => DefaultDB)
  extends ReactiveRepository[CachedCalculation, BSONObjectID](
    "calculation",
    mongo,
    CachedCalculation.formats) with CalculationRepository {

  val fieldName = "createdAt"
  val createdIndexName = "calculationResponseExpiry"
  val expireAfterSeconds = "expireAfterSeconds"
  val timeToLive = 600

  createIndex(fieldName, createdIndexName, timeToLive)

  private def createIndex(field: String, indexName: String, ttl: Int): Future[Boolean] = {
    collection.indexesManager.ensure(Index(Seq((field, IndexType.Ascending)), Some(indexName),
      options = BSONDocument(expireAfterSeconds -> ttl))) map {
      result => {
        // $COVERAGE-OFF$
        Logger.debug(s"set [$indexName] with value $ttl -> result : $result")
        // $COVERAGE-ON$
        result
      }
    } recover {
      // $COVERAGE-OFF$
      case e => Logger.error("Failed to set TTL index", e)
        false
      // $COVERAGE-ON$
    }
  }

  override def findByRequest(request: CalculationRequest): Future[Option[GmpCalculationResponse]] = {
    val tryResult = Try {
      collection.find(Json.obj("request" -> request.hashCode)).cursor[CachedCalculation](ReadPreference.primary).collect[List]()
    }

    tryResult match {
      case Success(s) => {
        s.map { x =>
          Logger.debug(s"[CalculationMongoRepository][findByRequest] : { request : $request, result: $x }")
          x.headOption.map(_.response)
        }
      }
      case Failure(f) => {
        Logger.debug(s"[CalculationMongoRepository][findByRequest] : { request : $request, exception: ${f.getMessage} }")
        Future.successful(None)
      }
    }
  }

  override def insertByRequest(request: CalculationRequest, response: GmpCalculationResponse): Future[Boolean] = {
    collection.insert(CachedCalculation(request.hashCode, response)).map { lastError =>
      Logger.debug(s"[CalculationMongoRepository][insertByRequest] : { request : $request, response: $response, result: ${lastError.ok}, errors: ${lastError.errmsg} }")
      lastError.ok
    }
  }
}

object CalculationRepository extends MongoDbConnection {

  private lazy val calculationRepository = new CalculationMongoRepository

  def apply(): CalculationMongoRepository = calculationRepository
}
