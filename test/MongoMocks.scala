/*
 * Copyright 2017 HM Revenue & Customs
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
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsObject
import reactivemongo.api.commands.{UpdateWriteResult, WriteConcern, WriteResult}
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.api.{CollectionProducer, Cursor, DefaultDB, FailoverStrategy}
import reactivemongo.json.collection.{JSONCollection, JSONQueryBuilder}

import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._

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
    when(collection.indexesManager).thenReturn(mockIndexManager)

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
    verify(collection).insert(any, any())(any(), any())
  }

  def verifyInsertOn[T](collection: JSONCollection, obj: T) = {
    verify(collection).insert(eqTo(obj), any())(any(), any())
  }

  def verifyInsertOn[T](collection: JSONCollection, captor: ArgumentCaptor[T]) = {
    verify(collection).insert(captor.capture(), any[WriteConcern])(any(), any[ExecutionContext])
  }

  def verifyUpdateOn[T](collection: JSONCollection, filter: Option[(JsObject) => Unit] = None, update: Option[(JsObject) => Unit] = None) = {
    val filterCaptor = ArgumentCaptor.forClass(classOf[JsObject])
    val updaterCaptor = ArgumentCaptor.forClass(classOf[JsObject])

    verify(collection).update(filterCaptor.capture(), updaterCaptor.capture(), any[WriteConcern], anyBoolean(), anyBoolean())(any(), any(), any[ExecutionContext])

    if (filter.isDefined) {
      filter.get(filterCaptor.getValue)
    }

    if (update.isDefined) {
      update.get(updaterCaptor.getValue)
    }
  }

  def verifyAnyUpdateOn[T](collection: JSONCollection) = {
    verify(collection).update(any(), any(), any[WriteConcern], anyBoolean(), anyBoolean())(any(), any(), any[ExecutionContext])
  }

  def setupFindFor[T](collection: JSONCollection, returns: Traversable[T])(implicit manifest: Manifest[T]) = {

    val queryBuilder = mock[JSONQueryBuilder]
    val cursor = mock[Cursor[T]]

    when(
      collection.find(any[JsObject])(any())
    ) thenReturn queryBuilder

    when(
      queryBuilder.cursor[T](any(), any())(any(), any[ExecutionContext], any())
    ) thenAnswer new Answer[Cursor[T]] {
      def answer(i: InvocationOnMock) = cursor
    }

    when(
      cursor.collect[Traversable](anyInt, anyBoolean)(any[CanBuildFrom[Traversable[_], T, Traversable[T]]], any[ExecutionContext])
    ) thenReturn Future.successful(returns)

  }

  def setupFindFor[T](collection: JSONCollection, returns: Option[T])(implicit manifest: Manifest[T]) = {

    val queryBuilder = mock[JSONQueryBuilder]

    when(
      collection.find(any[JsObject])(any())
    ) thenReturn queryBuilder

    when(
      queryBuilder.one[T](any(), any)
    ) thenReturn Future.successful(returns)

  }

  def setupFindFor[T](collection: JSONCollection, filter: Any, returns: Option[T])(implicit manifest: Manifest[T]) = {

    val queryBuilder = mock[JSONQueryBuilder]

    when(
      collection.find(eqTo(filter))(any())
    ) thenReturn queryBuilder

    when(
      queryBuilder.one[T](any(), any)
    ) thenReturn Future.successful(returns)

  }

  def setupFindFor[T](collection: JSONCollection, filter: Any, returns: Traversable[T])(implicit manifest: Manifest[T]) = {

    val queryBuilder = mock[JSONQueryBuilder]
    val cursor = mock[Cursor[T]]

    when(
      collection.find(eqTo(filter))(any())
    ) thenReturn queryBuilder

    when(
      queryBuilder.cursor[T](any(), any())(any(), any[ExecutionContext], any())
    ) thenAnswer new Answer[Cursor[T]] {
      def answer(i: InvocationOnMock) = cursor
    }

    when(
      cursor.collect[Traversable](anyInt, anyBoolean)(any[CanBuildFrom[Traversable[_], T, Traversable[T]]], any[ExecutionContext])
    ) thenReturn Future.successful(returns)

  }

  def setupInsertOn[T](collection: JSONCollection, obj: T, fails: Boolean = false) = {
    val m = mockWriteResult(fails)
    when(collection.insert(eqTo(obj), any())(any(), any()))
      .thenReturn(Future.successful(m))
  }

  def setupAnyInsertOn(collection: JSONCollection, fails: Boolean = false) = {
    val m = mockWriteResult(fails)
    when(collection.insert(any(), any())(any(), any()))
      .thenReturn(Future.successful(m))
  }

  def setupAnyUpdateOn(collection: JSONCollection, fails: Boolean = false) = {
    val m = mockUpdateWriteResult(fails)
    when(
      collection.update(any(), any(), any(), anyBoolean, anyBoolean)(any(), any(), any[ExecutionContext])
    ) thenReturn Future.successful(m)
  }
}
