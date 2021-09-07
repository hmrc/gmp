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
import models.GmpValidateSconResponse
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, JsObject, Json, OFormat}
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

case class ValidateSconMongoModel(scon: String,
                                  response: GmpValidateSconResponse,
                                  createdAt: DateTime = DateTime.now(DateTimeZone.UTC))

object ValidateSconMongoModel {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val formats: OFormat[ValidateSconMongoModel] = Json.format[ValidateSconMongoModel]
}

@ImplementedBy(classOf[ValidateSconMongoRepository])
trait ValidateSconRepository extends ReactiveRepository[ValidateSconMongoModel, BSONObjectID] {
  def findByScon(scon: String): Future[Option[GmpValidateSconResponse]]

  def insertByScon(scon: String, validateSconResponse: GmpValidateSconResponse): Future[Boolean]
}

@Singleton
class ValidateSconMongoRepository @Inject()(component: ReactiveMongoComponent)
  extends ReactiveRepository[ValidateSconMongoModel, BSONObjectID](
    "validate_scon",
    component.mongoConnector.db,
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
        logger.debug(s"set [$indexName] with value $ttl -> result : $result")
        result
      }
    } recover {
      case e => logger.error("Failed to set TTL index", e)
        false
    }
  }

  override def insertByScon(scon: String, validateSconResponse: GmpValidateSconResponse): Future[Boolean] = {
    val model = ValidateSconMongoModel(scon, validateSconResponse)
    collection.insert(ordered = false).one(model).map { lastError =>
      logger.debug(s"[ValidateSconMongoRepository][insertByScon] : { scon : $scon, result: ${lastError.ok}, errors: ${Message.unapply(lastError)} }")
      lastError.ok
    }
  }

  override def findByScon(scon: String): Future[Option[GmpValidateSconResponse]] = {
    val result = Try {
      collection.find(Json.obj("scon" -> scon), Option.empty[JsObject]).cursor[ValidateSconMongoModel](ReadPreference.primary)
        .collect[List](maxDocs = -1, err = Cursor.FailOnError[List[ValidateSconMongoModel]]())
    }

    result match {
      case Success(s) => {
        s.map {
          x =>
            logger.debug(s"[ValidateSconMongoRepository][findByScon] : { scon : $scon, result: $x }")
            x.headOption.map(_.response)
        }
      }
      case Failure(f) => {

        logger.debug(s"[ValidateSconMongoRepository][findByScon] : { scon : $scon, exception: ${f.getMessage} }")
        Future.successful(None)

      }
    }

  }
}
