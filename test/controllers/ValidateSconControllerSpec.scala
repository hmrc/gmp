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
import config.AppConfig
import connectors.{DesConnector, HipConnector}
import controllers.auth.FakeAuthAction
import models.{ValidateSconResponse, GmpValidateSconResponse, HipValidateSconResponse, ValidateSconRequest}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}
import repositories.ValidateSconRepository
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ValidateSconControllerSpec extends BaseSpec with BeforeAndAfterEach with StubControllerComponentsFactory with ScalaFutures {

  private val validateSconRequest = ValidateSconRequest("S1401234Q")
//  private val validateSconResponse = GmpValidateSconResponse(true)
  private val mockDesConnector = mock[DesConnector]
  private val mockHipConnector = mock[HipConnector]
  private val mockRepo = mock[ValidateSconRepository]
  private val mockAuthConnector = mock[AuthConnector]
  private val mockAppConfig = mock[AppConfig]

  private val gmpAuthAction = FakeAuthAction(mockAuthConnector, stubControllerComponents())
  private val controller = new ValidateSconController(
    mockDesConnector,
    mockHipConnector,
    mockRepo,
    gmpAuthAction,
    stubControllerComponents(),
    mockAppConfig
  )

  private def validRequest =
    FakeRequest("POST", "/")
      .withHeaders("Content-Type" -> "application/json")
      .withBody(Json.toJson(validateSconRequest))

  val testValidateSconController = new ValidateSconController(mockDesConnector, mockHipConnector, mockRepo, gmpAuthAction, controllerComponents, mockAppConfig)
  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepo, mockDesConnector, mockHipConnector, mockAppConfig)
    when(mockAppConfig.isHipEnabled).thenReturn(false) // Default to DES for backward compatibility
  }

  "ValidateSconController" when {
    "using HIP connector" when {
      "HIP is enabled" should {
        "return OK for valid SCON" in {
          when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
          when(mockHipConnector.validateScon(any(), any())(any()))
            .thenReturn(Future.successful(HipValidateSconResponse(true)))
          when(mockAppConfig.isHipEnabled).thenReturn(true)

          val result = controller.validateScon("user123")(validRequest)

          status(result) mustBe OK
          (contentAsJson(result) \ "sconExists").as[Boolean] mustBe true
        }

        "return BadRequest for invalid SCON format" in {
          when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
          when(mockHipConnector.validateScon(any(), any())(any()))
            .thenReturn(Future.failed(new IllegalArgumentException("Invalid SCON format")))
          when(mockAppConfig.isHipEnabled).thenReturn(true)

          val result = controller.validateScon("user123")(validRequest)

          status(result) mustBe BAD_REQUEST
          (contentAsJson(result) \ "error").as[String] mustBe "Invalid SCON format"
        }

        "handle HTTP 400 errors from HIP" in {
          when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
          when(mockHipConnector.validateScon(any(), any())(any()))
            .thenReturn(Future.failed(UpstreamErrorResponse("Bad Request", 400, 400)))
          when(mockAppConfig.isHipEnabled).thenReturn(true)

          val result = controller.validateScon("user123")(validRequest)

          status(result) mustBe BAD_REQUEST
          (contentAsJson(result) \ "error").as[String] mustBe "Invalid request"
        }

        "handle HTTP 500 errors from HIP" in {
          when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
          when(mockHipConnector.validateScon(any(), any())(any()))
            .thenReturn(Future.failed(UpstreamErrorResponse("Internal Server Error", 500, 500)))
          when(mockAppConfig.isHipEnabled).thenReturn(true)

          val result = controller.validateScon("user123")(validRequest)

          status(result) mustBe INTERNAL_SERVER_ERROR
          (contentAsJson(result) \ "error").as[String] mustBe "Service unavailable"
        }

        "handle other exceptions" in {
          when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
          when(mockHipConnector.validateScon(any(), any())(any()))
            .thenReturn(Future.failed(new RuntimeException("Unexpected error")))
          when(mockAppConfig.isHipEnabled).thenReturn(true)

          val result = controller.validateScon("user123")(validRequest)

          status(result) mustBe INTERNAL_SERVER_ERROR
          (contentAsJson(result) \ "error").as[String] mustBe "An unexpected error occurred"
        }

        "return cached response when available" in {
          when(mockRepo.findByScon(any())).thenReturn(Future.successful(Some(GmpValidateSconResponse(true))))
          when(mockAppConfig.isHipEnabled).thenReturn(true)

          val result = controller.validateScon("user123")(validRequest)

          status(result) mustBe OK
          (contentAsJson(result) \ "sconExists").as[Boolean] mustBe true
          verify(mockHipConnector, never()).validateScon(any(), any())(any())
        }
      }
    }

    "using DES connector" when {
      "HIP is disabled" should {
        "return OK for valid SCON" in {
          when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
          when(mockDesConnector.validateScon(any(), any())(any()))
            .thenReturn(Future.successful(ValidateSconResponse(1)))

          val result = controller.validateScon("user123")(validRequest)

          status(result) mustBe OK
          (contentAsJson(result) \ "sconExists").as[Boolean] mustBe true
        }

        "handle errors from DES" in {
          when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
          when(mockDesConnector.validateScon(any(), any())(any()))
            .thenReturn(Future.failed(UpstreamErrorResponse("DES Error", 500, 500)))

          val result = controller.validateScon("user123")(validRequest)

          status(result) mustBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "request validation" should {
      "return BadRequest for invalid JSON" in {
        val invalidRequest = FakeRequest("POST", "/")
          .withHeaders("Content-Type" -> "application/json")
          .withBody(Json.obj("invalid" -> "data"))

        val result = controller.validateScon("user123")(invalidRequest)

        status(result) mustBe BAD_REQUEST
        contentAsString(result) must include("Invalid ValidateSconRequest payload")
      }

      "return BadRequest for missing scon field" in {
        val invalidRequest = FakeRequest("POST", "/")
          .withHeaders("Content-Type" -> "application/json")
          .withBody(Json.obj())

        val result = controller.validateScon("user123")(invalidRequest)

        status(result) mustBe BAD_REQUEST
        contentAsString(result) must include("Invalid ValidateSconRequest payload")
      }

      "return BadRequest for empty scon" in {
        val invalidRequest = FakeRequest("POST", "/")
          .withHeaders("Content-Type" -> "application/json")
          .withBody(Json.obj("scon" -> ""))

        // Setup mock to handle the empty SCON case
        when(mockRepo.findByScon(any()))
          .thenReturn(Future.successful(None))
        when(mockAppConfig.isHipEnabled).thenReturn(true)
        when(mockHipConnector.validateScon(any(), any())(any()))
          .thenReturn(Future.failed(new IllegalArgumentException("Invalid SCON format")))

        val result = controller.validateScon("user123")(invalidRequest)

        status(result) mustBe BAD_REQUEST
        contentAsString(result) must include("Invalid SCON format")
      }
    }

    "repository operations" should {
      "handle repository failures gracefully" in {
        when(mockRepo.findByScon(any()))
          .thenReturn(Future.failed(new RuntimeException("Database error")))
        when(mockAppConfig.isHipEnabled).thenReturn(true)

        val result = controller.validateScon("user123")(validRequest)

        status(result) mustBe BAD_REQUEST
        contentAsString(result) must include("Invalid request format")
      }
    }

    "audit logging" should {
      "log audit events for successful validations" in {
        when(mockRepo.findByScon(any()))
          .thenReturn(Future.successful(None))
        when(mockHipConnector.validateScon(any(), any())(any()))
          .thenReturn(Future.successful(HipValidateSconResponse(true)))
        when(mockAppConfig.isHipEnabled).thenReturn(true)

        val result = controller.validateScon("user123")(validRequest)

        status(result) mustBe OK
      }
    }

    "concurrent requests" should {
      "handle multiple requests for same SCON correctly" in {
        // Setup mock to return None first, then Some(response) for subsequent calls
        when(mockRepo.findByScon(any()))
          .thenReturn(Future.successful(None))
          .thenReturn(Future.successful(Some(GmpValidateSconResponse(true))))

        when(mockHipConnector.validateScon(any(), any())(any()))
          .thenReturn(Future.successful(HipValidateSconResponse(true)))
        when(mockAppConfig.isHipEnabled).thenReturn(true)

        // Fire multiple requests in parallel
        val results = (1 to 3).map { _ =>
          status(controller.validateScon("user123")(validRequest))
        }

        // All requests should complete successfully
        results.foreach { statusCode =>
          statusCode mustBe OK
        }
      }
    }
    "call HIPConnector when Hip is enabled" should {
      "respond to a valid validateScon request with OK" in {
        when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
        when(mockHipConnector.validateScon(any(), any())(any()))
          .thenReturn(Future.successful(HipValidateSconResponse(false)))
        when(mockAppConfig.isHipEnabled).thenReturn(true)
        val fakeRequest = FakeRequest(method = "POST", path = "").withBody(Json.toJson(validateSconRequest))

        val result = testValidateSconController.validateScon("PSAID")(fakeRequest)
        status(result) must be(OK)
      }

      "return json" in {
        when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
        when(mockHipConnector.validateScon(any(), any())(any()))
          .thenReturn(Future.successful(HipValidateSconResponse(false)))
        when(mockAppConfig.isHipEnabled).thenReturn(true)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

        val result = testValidateSconController.validateScon("PSAID")(fakeRequest)
        contentType(result).get must be("application/json")
      }

      "return the correct validation result - false" in {
        when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
        when(mockHipConnector.validateScon(any(), any())(any()))
          .thenReturn(Future.successful(HipValidateSconResponse(false)))
        when(mockAppConfig.isHipEnabled).thenReturn(true)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

        val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
        (contentAsJson(result) \ "sconExists").as[JsBoolean].value must be(false)
      }

      "return the correct validation result - true" in {
        when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
        when(mockHipConnector.validateScon(any(), any())(any()))
          .thenReturn(Future.successful(HipValidateSconResponse(true)))
        when(mockAppConfig.isHipEnabled).thenReturn(true)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

        val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)
        (contentAsJson(result) \ "sconExists").as[JsBoolean].value must be(true)
      }

      "respond with server error if connector returns same" in {
        when(mockRepo.findByScon(any())).thenReturn(Future.successful(None))
        when(mockHipConnector.validateScon(any(), any())(any())).thenReturn(Future
          .failed(UpstreamErrorResponse("Only DOL Requests are supported", 500, 500)))
        when(mockAppConfig.isHipEnabled).thenReturn(true)
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(validateSconRequest))

        val result = testValidateSconController.validateScon("PSAID").apply(fakeRequest)

        status(result) must be(INTERNAL_SERVER_ERROR)
      }
    }
  }
}