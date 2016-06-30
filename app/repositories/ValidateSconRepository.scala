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

import models.GmpValidateSconResponse
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.Json
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{ReadPreference, DefaultDB}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{Repository, ReactiveRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Try, Failure, Success}

case class ValidateSconMongoModel(scon: String,
                                  response: GmpValidateSconResponse,
                                  createdAt: DateTime = DateTime.now(DateTimeZone.UTC))

object ValidateSconMongoModel {
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val idFormat = ReactiveMongoFormats.objectIdFormats
  implicit val formats = Json.format[ValidateSconMongoModel]
}

trait ValidateSconRepository extends Repository[ValidateSconMongoModel, BSONObjectID] {
  def findByScon(scon: String): Future[Option[GmpValidateSconResponse]]

  def insertByScon(scon: String, validateSconResponse: GmpValidateSconResponse): Future[Boolean]
}

object ValidateSconRepository extends MongoDbConnection {

  private lazy val repository = new ValidateSconMongoRepository

  def apply(): ValidateSconMongoRepository = repository
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

  override def insertByScon(scon: String, validateSconResponse: GmpValidateSconResponse): Future[Boolean] = {
    val model = ValidateSconMongoModel(scon, validateSconResponse)
    collection.insert(model).map { lastError =>
      Logger.debug(s"[ValidateSconMongoRepository][insertByScon] : { scon : $scon, result: ${lastError.ok}, errors: ${lastError.errmsg} }")
      lastError.ok
    }
  }


  override def findByScon(scon: String): Future[Option[GmpValidateSconResponse]] = {
    val result = Try {
      collection.find(Json.obj("scon" -> scon)).cursor[ValidateSconMongoModel](ReadPreference.primary).collect[List]()
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

