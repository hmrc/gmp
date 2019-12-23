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

package repositories

import com.google.inject.{Inject, Provider, Singleton}
import models.GmpValidateSconResponse
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, Json, OFormat}
import play.modules.reactivemongo.{MongoDbConnection, ReactiveMongoComponent}
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

case class ValidateSconMongoModel(scon: String,
                                  response: GmpValidateSconResponse,
                                  createdAt: DateTime = DateTime.now(DateTimeZone.UTC))

object ValidateSconMongoModel {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val formats: OFormat[ValidateSconMongoModel] = Json.format[ValidateSconMongoModel]
}

trait ValidateSconRepository extends ReactiveRepository[ValidateSconMongoModel, BSONObjectID] {
  def findByScon(scon: String): Future[Option[GmpValidateSconResponse]]

  def insertByScon(scon: String, validateSconResponse: GmpValidateSconResponse): Future[Boolean]
}

@Singleton
class ValidateSconRepositoryProvider @Inject()(component: ReactiveMongoComponent) extends Provider[ValidateSconRepository] {
  override def get(): ValidateSconRepository = {
    new ValidateSconMongoRepository()(component.mongoConnector.db)
  }
}

class ValidateSconMongoRepository()(implicit mongo: () => DefaultDB)
  extends ReactiveRepository[ValidateSconMongoModel, BSONObjectID](
    "validate_scon",
    mongo,
    ValidateSconMongoModel.formats) with ValidateSconRepository {

  val fieldName = "createdAt"
  val createdIndexName = "sconValidationResponseExpiry"
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

  override def insertByScon(scon: String, validateSconResponse: GmpValidateSconResponse): Future[Boolean] = {
    val model = ValidateSconMongoModel(scon, validateSconResponse)
    collection.insert(ordered = false).one(model).map { lastError =>
      Logger.debug(s"[ValidateSconMongoRepository][insertByScon] : { scon : $scon, result: ${lastError.ok}, errors: ${Message.unapply(lastError)} }")
      lastError.ok
    }
  }

  override def findByScon(scon: String): Future[Option[GmpValidateSconResponse]] = {
    val result = Try {
      collection.find(Json.obj("scon" -> scon)).cursor[ValidateSconMongoModel](ReadPreference.primary)
        .collect[List](maxDocs = -1, err = Cursor.FailOnError[List[ValidateSconMongoModel]]())
    }

    result match {
      case Success(s) => {
        s.map {
          x =>
            Logger.debug(s"[ValidateSconMongoRepository][findByScon] : { scon : $scon, result: $x }")
            x.headOption.map(_.response)
        }
      }
      case Failure(f) => {

        Logger.debug(s"[ValidateSconMongoRepository][findByScon] : { scon : $scon, exception: ${f.getMessage} }")
        Future.successful(None)

      }
    }

  }
}

object ValidateSconRepository {

  implicit val db : scala.Function0[reactivemongo.api.DefaultDB] = (GuiceApplicationBuilder().injector().instanceOf[ReactiveMongoComponent]).mongoConnector.db

  private lazy val repository = new ValidateSconMongoRepository

  def apply(): ValidateSconMongoRepository = repository
}
