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

package helpers.mongo

import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsObject
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.api.{CollectionProducer, DefaultDB, FailoverStrategy}
import reactivemongo.play.json.collection.JSONCollection
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import reactivemongo.play.json.ImplicitBSONHandlers.BSONDocumentWrites

trait MongoMocks extends MockitoSugar {

  implicit val mockMongoDb = mock[DefaultDB]

  def mockCollection(name: Option[String] = None)(implicit db: DefaultDB, ec: ExecutionContext): JSONCollection = {
    val collection = mock[JSONCollection]

    val matcher = name match {
      case Some(x) => eqTo(x)
      case _ => any()
    }

    when(db.collection(matcher, any[FailoverStrategy])
    (any[CollectionProducer[JSONCollection]]()))
      .thenReturn(collection)

    val mockIndexManager = mock[CollectionIndexesManager]
    when(mockIndexManager.ensure(any())).thenReturn(Future.successful(true))
    when(collection.indexesManager(ec)).thenReturn(mockIndexManager)

    setupAnyUpdateOn(collection)
    setupAnyInsertOn(collection)

    collection
  }

  def mockWriteResult(fails: Boolean = false) = {
    val m = mock[WriteResult]
    when(m.ok).thenReturn(!fails)
    m
  }

  def mockUpdateWriteResult(fails: Boolean = false) = {
    val m = mock[UpdateWriteResult]
    when(m.ok).thenReturn(!fails)
    m
  }

  def verifyAnyInsertOn(collection: JSONCollection) = {
    verify(collection).insert(ordered = false).one(any())
  }

  def verifyUpdateOn[T](collection: JSONCollection, filter: Option[(JsObject) => Unit] = None, update: Option[(JsObject) => Unit] = None) = {
    val filterCaptor = ArgumentCaptor.forClass(classOf[JsObject])
    val updaterCaptor = ArgumentCaptor.forClass(classOf[JsObject])

    verify(collection).update(ordered = false).one(
      filterCaptor.capture(),
      updaterCaptor.capture(),
      any(),
      any(),
      any()
    )(any(), any(), any())

    if (filter.isDefined) {
      filter.get(filterCaptor.getValue)
    }

    if (update.isDefined) {
      update.get(updaterCaptor.getValue)
    }
  }

  def verifyAnyUpdateOn[T](collection: JSONCollection) = {
    verify(collection).update(ordered = false).one(any(), any(), any(), any(), any())(any(), any(), any())
  }

  def setupInsertOn[T](collection: JSONCollection, obj: T, fails: Boolean = false) = {
    val m = mockWriteResult(fails)
    when(collection.insert(ordered = false).one((eqTo(obj), any()))(any(), any()))
      .thenReturn(Future.successful(m))
  }

  def setupAnyInsertOn(collection: JSONCollection, fails: Boolean = false) = {
    val m = mockWriteResult(fails)
    when(collection.insert(ordered = false).one(any()))
      .thenReturn(Future.successful(m))
  }

  def setupAnyUpdateOn(collection: JSONCollection, fails: Boolean = false) = {
    val m = mockUpdateWriteResult(fails)
    when(
      collection.update(ordered = false).one(any(), any(), any(), any(), any())(any(), any(), any())
    ) thenReturn Future.successful(m)
  }
}
