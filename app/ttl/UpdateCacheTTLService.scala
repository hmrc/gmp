/*
 * Copyright 2025 HM Revenue & Customs
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

package ttl

import javax.inject.{Inject, Singleton}
import com.mongodb.ErrorCategory
import org.bson.BsonType
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{Document, MongoCollection, MongoWriteException, SingleObservableFuture}
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpdateCacheTTLService @Inject() ( mongo: MongoComponent)(implicit val ec: ExecutionContext) extends Logging {

  private val calculationCollection: MongoCollection[Document] =
    mongo.database.getCollection("calculation")

  private val sconCollection: MongoCollection[Document] =
    mongo.database.getCollection("validate_scon")

  private val lockCollection: MongoCollection[Document] =
    mongo.database.getCollection("gmp-cache-locks")

  private val lockId = "update-cache-ttl-lock"
  
  // Trigger at the time of Startup
  for {
    _ <- updateItem(sconCollection)
    _ <- updateItem(calculationCollection)
  } yield ()


  private def acquireLock(): Future[Boolean] = {
    val lockDoc = Document("_id" -> lockId, "createdAt" -> new Date())
    lockCollection.insertOne(lockDoc).toFuture().map(_ => true).recover {
      case ex: MongoWriteException if ex.getError.getCategory == ErrorCategory.DUPLICATE_KEY => {
        logger.info("Lock already exists. Skipping current job.")
        false
      }
      case ex => {
        logger.error("Unexpected error while acquiring lock", ex)
        false
      }
    }
  }


  private def dropLockCollection(reason: String): Future[Unit] = {
    logger.info(s"Dropping lock collection due to: $reason")
    lockCollection
      .drop()
      .toFuture()
      .map { _ =>
        logger.info("Lock collection dropped successfully.")
      }
      .recover { case ex =>
        logger.error("Failed to drop lock collection", ex)
      }
  }

  def updateItem(collection: MongoCollection[Document]): Future[Unit] =
    acquireLock().flatMap {
      case true =>
        logger.info("Lock acquired. Starting aggregation-based update.")
        val createdATFilter = Filters.`type`("createdAt", BsonType.STRING)

        val updatePipeline = List(
          Document(
            "$set" -> Document(
              "createdAt"   -> Document("$toDate" -> "$createdAt")
            )
          )
        )

        collection
          .updateMany(
            Filters.and(
              createdATFilter
            ),
            updatePipeline
          )
          .toFuture()
          .map { result =>
            logger.info(s"Aggregation update completed: ${result.getModifiedCount} documents updated.")
          }
          .recoverWith { case ex =>
            logger.error("Aggregation update failed", ex)
            // Drop collection in case of failure
            dropLockCollection("Aggregation failure")
          }
          .flatMap { _ =>
            // Drop collection after successful update
            dropLockCollection("Successful update")
          }

      case false =>
        Future.successful(())
    }
}
