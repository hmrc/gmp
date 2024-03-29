/*
 * Copyright 2024 HM Revenue & Customs
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

import base.BaseSpec
import java.util.UUID
import connectors.DesConnector
import controllers.auth.FakeAuthAction
import models.{GmpValidateSconResponse, ValidateSconRequest, ValidateSconResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.{JsBoolean, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import repositories.ValidateSconRepository
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ValidateSconControllerSpec extends BaseSpec {

  val validateSconRequest = ValidateSconRequest(UUID.randomUUID().toString)
  val validateSconResponse = GmpValidateSconResponse(true)
  val mockDesConnector = mock[DesConnector]
  val mockRepo = mock[ValidateSconRepository]
  val mockMicroserviceAuthConnector = mock[AuthConnector]
  val mockAuthConnector = mock[AuthConnector]

  val gmpAuthAction = FakeAuthAction(mockAuthConnector, controllerComponents)

  before {
    reset(mockRepo)
    reset(mockDesConnector)
  }

  val testValidateSconController = new ValidateSconController(mockDesConnector, mockRepo, gmpAuthAction, controllerComponents)

  "ValidateSconController" should {

    "respond to a valid validateScon request with OK" in {
      when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(any(), any())(any()))
        .thenReturn(Future.successful(ValidateSconResponse(0)))

      val fakeRequest = FakeRequest(method = "POST", path = "").withBody(Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID")(fakeRequest)
      status(result) must be(OK)
    }

    "return json" in {
      when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(any(), any())(any()))
        .thenReturn(Future.successful(ValidateSconResponse(0)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID")(fakeRequest)
      contentType(result).get must be("application/json")
    }

    "return the correct validation result - false" in {
      when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(any(), any())(any()))
        .thenReturn(Future.successful(ValidateSconResponse(0)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
      (contentAsJson(result) \ "sconExists").as[JsBoolean].value must be(false)
    }

    "return the correct validation result - true" in {
      when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(any(), any())(any()))
        .thenReturn(Future.successful(ValidateSconResponse(1)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
      (contentAsJson(result) \ "sconExists").as[JsBoolean].value must be(true)
    }

    "respond with server error if connector returns same" in {
      when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
      when(mockDesConnector.validateScon(any(), any())(any())).thenReturn(Future
        .failed(UpstreamErrorResponse("Only DOL Requests are supported", 500, 500)))

      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

      val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)

      status(result) must be(INTERNAL_SERVER_ERROR)
    }

    "return cached response" in {
      when(mockRepo.findByScon(any())).thenReturn(Future.successful(Some(validateSconResponse)))
      val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))
      await(testValidateSconController.validateScon("PSAID").apply(fakeRequest))

      val cachedResult = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
      (contentAsJson(cachedResult) \ "sconExists").as[JsBoolean].value must be(true)
      verify(mockDesConnector, never()).validateScon(any(), any())(any())
    }
  }
}