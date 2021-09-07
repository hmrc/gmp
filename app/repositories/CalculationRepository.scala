/*
 * Copyright 2021 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import models.{CalculationRequest, GmpCalculationResponse}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult.Message
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class CachedCalculation(request: Int,
                             response: GmpCalculationResponse,
                             createdAt: DateTime = DateTime.now(DateTimeZone.UTC))

object CachedCalculation {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val formats = Json.format[CachedCalculation]
}

@ImplementedBy(classOf[CalculationMongoRepository])
trait CalculationRepository extends ReactiveRepository[CachedCalculation, BSONObjectID] {

  def findByRequest(request: CalculationRequest): Future[Option[GmpCalculationResponse]]

  def insertByRequest(request: CalculationRequest, response: GmpCalculationResponse): Future[Boolean]

}

@Singleton
class CalculationMongoRepository @Inject()(component: ReactiveMongoComponent)
  extends ReactiveRepository[CachedCalculation, BSONObjectID](
    "calculation",
    component.mongoConnector.db,
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
        logger.debug(s"set [$indexName] with value $ttl -> result : $result")
        result
      }
    } recover {
      case e => logger.error("Failed to set TTL index", e)
        false
    }
  }

  override def findByRequest(request: CalculationRequest): Future[Option[GmpCalculationResponse]] = {
    val tryResult = Try {
      collection.find(Json.obj("request" -> request.hashCode), Option.empty[JsObject]).cursor[CachedCalculation](ReadPreference.primary)
        .collect[List](maxDocs = -1, err = Cursor.FailOnError[List[CachedCalculation]]())
    }

    tryResult match {
      case Success(s) => {
        s.map { x =>
          logger.debug(s"[CalculationMongoRepository][findByRequest] : { request : $request, result: $x }")
          x.headOption.map(_.response)
        }
      }
      case Failure(f) => {
        logger.debug(s"[CalculationMongoRepository][findByRequest] : { request : $request, exception: ${f.getMessage} }")
        Future.successful(None)
      }
    }
  }

  override def insertByRequest(request: CalculationRequest, response: GmpCalculationResponse): Future[Boolean] = {
    collection.insert(ordered = false).one(CachedCalculation(request.hashCode, response)).map { lastError =>
      logger.debug(s"[CalculationMongoRepository][insertByRequest] : { request : $request, response: $response, result: ${lastError.ok}, errors: ${Message.unapply(lastError)} }")
      lastError.ok
    }
  }
}
