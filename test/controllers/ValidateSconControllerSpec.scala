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

package controllers

import java.util.UUID

import connectors.DesConnector
import models.{GmpValidateSconResponse, ValidateSconRequest, ValidateSconResponse}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsBoolean, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import repositories.ValidateSconRepository
import play.api.inject.guice.GuiceApplicationBuilder
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, Upstream5xxResponse }

class ValidateSconControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfter {

  implicit val hc = HeaderCarrier()

  implicit lazy override val app = new GuiceApplicationBuilder().build()

  val validateSconRequest = ValidateSconRequest(UUID.randomUUID().toString)
  val validateSconResponse = GmpValidateSconResponse(true)
  val mockDesConnector = mock[DesConnector]
  val mockRepo = mock[ValidateSconRepository]

  object testValidateSconController extends ValidateSconController(mockDesConnector, mockRepo)

  before {
    reset(mockRepo)
    reset(mockDesConnector)
  }

  "ValidateSconController" should {

    "respond to a valid validateScon request with OK" in {
      when(mockRepo.findByScon(Matchers.any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ValidateSconResponse(0)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
      status(result) must be(OK)
    }

    "return json" in {
      when(mockRepo.findByScon(Matchers.any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ValidateSconResponse(0)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
      contentType(result).get must be("application/json")
    }

    "return the correct validation result - false" in {
      when(mockRepo.findByScon(Matchers.any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ValidateSconResponse(0)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
      (contentAsJson(result) \ "sconExists").as[JsBoolean].value must be(false)
    }

    "return the correct validation result - true" in {
      when(mockRepo.findByScon(Matchers.any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(ValidateSconResponse(1)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
      (contentAsJson(result) \ "sconExists").as[JsBoolean].value must be(true)
    }

    "respond with server error if connector returns same" in {
      when(mockRepo.findByScon(Matchers.any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future
        .failed(new Upstream5xxResponse("Only DOL Requests are supported", 500, 500)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)

      status(result) must be(INTERNAL_SERVER_ERROR)
    }

    "return cached response" in {
      when(mockRepo.findByScon(Matchers.any())).thenReturn(Future.successful(Some(validateSconResponse)))
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))
      await(testValidateSconController.validateScon("PSAID").apply(fakeRequest))

      val cachedResult = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
      (contentAsJson(cachedResult) \ "sconExists").as[JsBoolean].value must be(true)
      verify(mockDesConnector, never()).validateScon(Matchers.any(), Matchers.any())(Matchers.any())
    }
  }
}
