/*
 * Copyright 2020 HM Revenue & Customs
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

import com.google.inject.{Inject, Provider, Singleton}
import models.{CalculationRequest, GmpCalculationResponse}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult.Message
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DefaultDB, ReadPreference}
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

trait CalculationRepository extends ReactiveRepository[CachedCalculation, BSONObjectID] {

  def findByRequest(request: CalculationRequest): Future[Option[GmpCalculationResponse]]

  def insertByRequest(request: CalculationRequest, response: GmpCalculationResponse): Future[Boolean]

}

@Singleton
class CalculationRepositoryProvider @Inject()(component: ReactiveMongoComponent) extends Provider[CalculationRepository] {
  override def get(): CalculationRepository = {
    new CalculationMongoRepository()(component.mongoConnector.db)
  }
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
        Logger.debug(s"set [$indexName] with value $ttl -> result : $result")
        result
      }
    } recover {
      case e => Logger.error("Failed to set TTL index", e)
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
    collection.insert(ordered = false).one(CachedCalculation(request.hashCode, response)).map { lastError =>
      Logger.debug(s"[CalculationMongoRepository][insertByRequest] : { request : $request, response: $response, result: ${lastError.ok}, errors: ${Message.unapply(lastError)} }")
      lastError.ok
    }
  }
}

object CalculationRepository {

  implicit val db : scala.Function0[reactivemongo.api.DefaultDB] = (GuiceApplicationBuilder().injector().instanceOf[ReactiveMongoComponent]).mongoConnector.db

  private lazy val calculationRepository = new CalculationMongoRepository

  def apply(): CalculationMongoRepository = calculationRepository
}
