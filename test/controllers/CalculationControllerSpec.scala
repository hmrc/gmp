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

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import connectors.{DesConnector, DesGetHiddenRecordResponse, DesGetSuccessResponse, HipConnector, IFConnector, IFGetSuccessResponse}
import controllers.auth.FakeAuthAction
import models.{CalculationRequest, CalculationResponse, GmpCalculationResponse, HipCalcResult, HipCalculationFailuresResponse, HipCalculationResponse, HipFailure}
import HipCalcResult.{Failures, Success}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import repositories.CalculationRepository
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalculationControllerSpec extends BaseSpec {

  val calculationRequest = CalculationRequest("S1301234T", "AB123456C", "Smith", "Bill", Some(0), None, None, dualCalc = Some(1))
  val calculationResponse = GmpCalculationResponse("Bill Smith", "AB123456C", "S1301234T", None, None, List(), 0, None, None, None, dualCalc = true, 1)

  val dualCalcCalculationRequest = CalculationRequest("S1301234T", "AB123456C", "Smith", "Bill", Some(0), None, None, dualCalc = Some(0))
  val dualCalcCalculationResponse = GmpCalculationResponse("Bill Smith", "AB123456C", "S1301234T", None, None, List(), 0, None, None, None, dualCalc = false, 1)

  val mockDesConnector: DesConnector = mock[DesConnector]
  val mockIfConnector: IFConnector = mock[IFConnector]
  val mockHipConnector: HipConnector = mock[HipConnector]
  val mockRepo: CalculationRepository = mock[CalculationRepository]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockControllerComponents: ControllerComponents = stubMessagesControllerComponents()
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val hipMappingService = new services.HipMappingService()

  val gmpAuthAction = FakeAuthAction(mockAuthConnector, controllerComponents)

  val testCalculationController = new CalculationController(
    desConnector = mockDesConnector,
    ifConnector = mockIfConnector,
    hipConnector = mockHipConnector,
    repository = mockRepo,
    authAction = gmpAuthAction,
    auditConnector = mockAuditConnector,
    hipMappingService = hipMappingService,
    cc = mockControllerComponents,
    appConfig = mockAppConfig)

  before {
    reset(mockRepo)
    reset(mockDesConnector)
    reset(mockIfConnector)
    reset(mockHipConnector)
    reset(mockAuditConnector)
    
    // Default mock configurations
    when(mockAppConfig.isIfsEnabled).thenReturn(true)
    when(mockAppConfig.isHipEnabled).thenReturn(true)
    
    // Default repository behavior - return None for cache miss
    when(mockRepo.findByRequest(any()))
      .thenReturn(Future.successful(None))
    when(mockRepo.insertByRequest(any(), any()))
      .thenReturn(Future.successful(true))
    
    // Default connector behaviors
    when(mockIfConnector.getPersonDetails(any())(any()))
      .thenReturn(Future.successful(IFGetSuccessResponse))
    when(mockDesConnector.getPersonDetails(any())(any()))
      .thenReturn(Future.successful(DesGetSuccessResponse))
    
    // Default audit behavior
    when(mockAuditConnector.sendEvent(any())(any(), any()))
      .thenReturn(Future.successful(AuditResult.Success))
  }

  private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  "CalculationController" must {
    "when calculation is not in the cache" must {
      "must respond to a valid calculation request with OK using Des connector" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockDesConnector.calculate(any(), any())(any()))
          .thenReturn(Future
            .successful(Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 1.11,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0
              }
              ]
              }"""
            ).as[CalculationResponse]))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
        (contentAsJson(result) \ "globalErrorCode").as[Int] mustBe 0
      }

      "respond to a valid calculation request with OK using IF connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockIfConnector.calculate(any(), any())(any()))
          .thenReturn(Future
            .successful(Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 1.11,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0
              }
              ]
              }"""
            ).as[CalculationResponse]))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
      }

      "respond to a valid calculation request with OK using HIP connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockHipConnector.calculate(any(), any())(any()))
          .thenReturn(Future
            .successful(Success(Json.parse(
              """{
                "nationalInsuranceNumber": "AA000001A",
                "schemeContractedOutNumberDetails": "S2123456B",
                "payableAgeDate": "2022-06-27",
                "statePensionAgeDate": "2022-06-27",
                "dateOfDeath": "2022-06-27",
                "GuaranteedMinimumPensionDetailsList": [
                  {
                  "schemeMembershipStartDate": "2022-06-27",
                  "schemeMembershipEndDate": "2022-06-27",
                  "revaluationRate": "(NONE)",
                  "post1988GMPContractedOutDeductionsValue": 10.56,
                  "gmpContractedOutDeductionsAllRateValue": 10.56,
                  "gmpErrorCode": "Input revaluation date is before the termination date held on hmrc records",
                  "revaluationCalculationSwitchIndicator": true,
                  "post1990GMPContractedOutTrueSexTotal": 10.56,
                  "post1990GMPContractedOutOppositeSexTotal": 10.56,
                  "inflationProofBeyondDateofDeath": true,
                  "contributionsAndEarningsDetailsList": []
                  }
                ]
                }"""
            ).as[HipCalculationResponse])))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
      }

      "return json using Des connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockDesConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(Json.parse(
            """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 2.22,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0
              }
              ]
              }""").as[CalculationResponse]))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        contentType(result).get must be("application/json")
      }

      "return json using IF connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockIfConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(Json.parse(
            """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 2.22,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0
              }
              ]
              }""").as[CalculationResponse]))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        contentType(result).get must be("application/json")
      }

      "return json using HIP connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockHipConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(Success(Json.parse(
            """{
                "nationalInsuranceNumber": "AA000001A",
                "schemeContractedOutNumberDetails": "S2123456B",
                "payableAgeDate": "2022-06-27",
                "statePensionAgeDate": "2022-06-27",
                "dateOfDeath": "2022-06-27",
                "GuaranteedMinimumPensionDetailsList": [
                  {
                  "schemeMembershipStartDate": "2022-06-27",
                  "schemeMembershipEndDate": "2022-06-27",
                  "revaluationRate": "(NONE)",
                  "post1988GMPContractedOutDeductionsValue": 10.56,
                  "gmpContractedOutDeductionsAllRateValue": 10.56,
                  "gmpErrorCode": "Input revaluation date is before the termination date held on hmrc records",
                  "revaluationCalculationSwitchIndicator": true,
                  "post1990GMPContractedOutTrueSexTotal": 10.56,
                  "post1990GMPContractedOutOppositeSexTotal": 10.56,
                  "inflationProofBeyondDateofDeath": true,
                  "contributionsAndEarningsDetailsList": []
                  }
                ]
                }"""
          ).as[HipCalculationResponse])))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        contentType(result).get must be("application/json")
      }

      "return a Calculation Response with the correct SCON with Des Connector" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockDesConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(

            Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "spa_date": "2016-04-21",
              "payable_age_date": "2016-04-21",
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 3.33,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0,
                "gmp_cod_p90_ts_tot":0,
                "gmp_cod_p90_os_tot":0,
                "inflation_proof_beyond_dod":0,
                "npsLcntearn":[{
                       "rattd_tax_year": 1996,
                        "contributions_earnings": 1440
                      }]
              }
              ]
              }"""
            ).as[CalculationResponse]
          ))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(calculationRequest.copy(revaluationDate = Some("1990-01-01"), revaluationRate = Some(1))))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "scon").as[JsString].value must be("S1301234T")
        (contentAsJson(result) \ "dualCalc").as[JsBoolean].value must be(true)
      }

      "return a Calculation Response with the correct SCON using IF connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockIfConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(

            Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "spa_date": "2016-04-21",
              "payable_age_date": "2016-04-21",
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 3.33,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0,
                "gmp_cod_p90_ts_tot":0,
                "gmp_cod_p90_os_tot":0,
                "inflation_proof_beyond_dod":0,
                "npsLcntearn":[{
                       "rattd_tax_year": 1996,
                        "contributions_earnings": 1440
                      }]
              }
              ]
              }"""
            ).as[CalculationResponse]
          ))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(calculationRequest.copy(revaluationDate = Some("1990-01-01"), revaluationRate = Some(1))))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "scon").as[JsString].value must be("S1301234T")
        (contentAsJson(result) \ "dualCalc").as[JsBoolean].value must be(true)
      }

      

      "respond with server error if des connector returns same" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockDesConnector.calculate(any(), any())(any())).thenReturn(Future
          .failed(UpstreamErrorResponse("Only DOL Requests are supported", 500, 500)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "respond with server error if IF connector returns same" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockIfConnector.calculate(any(), any())(any())).thenReturn(Future
          .failed(UpstreamErrorResponse("Only DOL Requests are supported", 500, 500)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "respond with server error if HIP connector returns same" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockHipConnector.calculate(any(), any())(any())).thenReturn(Future
          .failed(UpstreamErrorResponse("Only DOL Requests are supported", 500, 500)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        status(result) must be(INTERNAL_SERVER_ERROR)
      }

      "contain revalued amounts when revaluation requested using Des connector" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockDesConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(
            Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 2.23,
              "gmp_cod_allrate_tot": 8.88,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 1
              }
              ]
              }"""
            ).as[CalculationResponse]
          ))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "scon").as[JsString].value must be("S1301234T")
        (contentAsJson(result) \ "calculationPeriods").as[Array[JsValue]].length must be(1)
        (contentAsJson(result) \ "calculationPeriods").as[Seq[JsValue]].head.\("gmpTotal").as[JsString].value must be("8.88")

      }

      "contain revalued amounts when revaluation requested using IF connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockIfConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(
            Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 2.23,
              "gmp_cod_allrate_tot": 8.88,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 1
              }
              ]
              }"""
            ).as[CalculationResponse]
          ))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "scon").as[JsString].value must be("S1301234T")
        (contentAsJson(result) \ "calculationPeriods").as[Array[JsValue]].length must be(1)
        (contentAsJson(result) \ "calculationPeriods").as[Seq[JsValue]].head.\("gmpTotal").as[JsString].value must be("8.88")

      }

      

      "return a Calculation Response with no revalued amounts when not revalued using Des connector" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockDesConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(

            Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 7.88,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0
              }
              ]
              }"""
            ).as[CalculationResponse]
          ))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        (contentAsJson(result) \ "calculationPeriods").as[Seq[JsValue]].head.\("revaluedGmpTotal").getClass must be(classOf[JsUndefined])
      }

      "return a Calculation Response with no revalued amounts when not revalued using DES connector" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockDesConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(

            Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 7.88,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0
              }
              ]
              }"""
            ).as[CalculationResponse]
          ))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        (contentAsJson(result) \ "calculationPeriods").as[Seq[JsValue]].head.\("revaluedGmpTotal").getClass must be(classOf[JsUndefined])
      }

      "return a Calculation Response with no revalued amounts when not revalued using IF connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockIfConnector.calculate(any(), any())(any())).thenReturn(Future
          .successful(

            Json.parse(
              """{
              "nino": "AB123456C",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
              },
              "npsLgmpcalc": [
              {
              "scheme_mem_start_date": "1978-04-06",
              "scheme_end_date": "2006-04-05",
              "revaluation_rate": 1,
              "gmp_cod_post_eightyeight_tot": 1.23,
              "gmp_cod_allrate_tot": 7.88,
              "gmp_error_code": 0,
              "reval_calc_switch_ind": 0
              }
              ]
              }"""
            ).as[CalculationResponse]
          ))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        (contentAsJson(result) \ "calculationPeriods").as[Seq[JsValue]].head.\("revaluedGmpTotal").getClass must be(classOf[JsUndefined])
      }

      

      "return an OK when http status code 422 with DES connector" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        val npsResponse = Json.parse(
          """{
              "nino": "AB123456",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
             },
             "npsLgmpcalc":[{
                        "scheme_mem_start_date": "1978-04-06",
                       "scheme_end_date": "2010-05-10",
                        "revaluation_rate": 1,
                        "gmp_cod_allrate_tot": 7.88,
                        "gmp_cod_post_eightyeight_tot": 1.23,
                        "gmp_error_code": 0,
                        "reval_calc_switch_ind": 0

                }]
              }"""
        ).as[CalculationResponse]

        when(mockDesConnector.calculate(any(), any())(any())).thenReturn(Future.successful(npsResponse))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
      }

      "return an OK when http status code 422 with IF connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        val npsResponse = Json.parse(
          """{
              "nino": "AB123456",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
             },
             "npsLgmpcalc":[{
                        "scheme_mem_start_date": "1978-04-06",
                       "scheme_end_date": "2010-05-10",
                        "revaluation_rate": 1,
                        "gmp_cod_allrate_tot": 7.88,
                        "gmp_cod_post_eightyeight_tot": 1.23,
                        "gmp_error_code": 0,
                        "reval_calc_switch_ind": 0

                }]
              }"""
        ).as[CalculationResponse]

        when(mockIfConnector.calculate(any(), any())(any())).thenReturn(Future.successful(npsResponse))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
      }

      "return an OK when http status code 422 with HIP connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        val failures = HipCalculationFailuresResponse(List(HipFailure("No match for person details provided", 63119)))
        when(mockHipConnector.calculate(any(), any())(any())).thenReturn(Future.successful(Failures(failures)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
        (contentAsJson(result) \ "globalErrorCode").as[Int] mustBe 63119
      }

      "return an OK when 422 with HIP connector and empty failures" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        val failures = HipCalculationFailuresResponse(Nil)
        when(mockHipConnector.calculate(any(), any())(any())).thenReturn(Future.successful(Failures(failures)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
        (contentAsJson(result) \ "globalErrorCode").as[Int] mustBe 0
        (contentAsJson(result) \ "calculationPeriods").as[Seq[JsValue]] mustBe empty
      }

      "return an OK when 422 with HIP connector and multiple failures (use first)" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        val failures = HipCalculationFailuresResponse(List(HipFailure("Some reason", 63166), HipFailure("Another", 56010)))
        when(mockHipConnector.calculate(any(), any())(any())).thenReturn(Future.successful(Failures(failures)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
        (contentAsJson(result) \ "globalErrorCode").as[Int] mustBe 63166
      }
    }

    "when calculation in the cache" must {

      "return OK" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(Some(calculationResponse)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        status(result) must be(OK)
      }


      "not call NPS using DES connector" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(Some(calculationResponse)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        verify(mockDesConnector, never()).calculate(any(), any())(any())
      }

      "not call NPS using IF connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(Some(calculationResponse)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        verify(mockIfConnector, never()).calculate(any(), any())(any())
      }

      "not call NPS using HIP connector" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(Some(calculationResponse)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))

        testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        verify(mockHipConnector, never()).calculate(any(), any())(any())
      }
    }

    "when dual calculation" must {
      "return false" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(Some(dualCalcCalculationResponse)))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(dualCalcCalculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "dualCalc").as[JsBoolean].value must be(false)
      }
    }

    "when dual calculation with no cache with DES connector" must {
      "return false" in {
        val npsResponse = Json.parse(
          """{
              "nino": "AB123456",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
             },
             "npsLgmpcalc":[{
                        "scheme_mem_start_date": "1978-04-06",
                       "scheme_end_date": "2010-05-10",
                        "revaluation_rate": 1,
                        "gmp_cod_allrate_tot": 7.88,
                        "gmp_cod_post_eightyeight_tot": 1.23,
                        "gmp_error_code": 0,
                        "reval_calc_switch_ind": 0

                }]
              }"""
        ).as[CalculationResponse]
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockDesConnector.calculate(any(), any())(any())).thenReturn(Future.successful(npsResponse))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(dualCalcCalculationRequest.copy(dualCalc = None)))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "dualCalc").as[JsBoolean].value must be(false)
      }
    }

    "when dual calculation with no cache" must {
      "return false" in {
        val npsResponse = Json.parse(
          """{
              "nino": "AB123456",
              "rejection_reason": 0,
              "npsScon": {
              "contracted_out_prefix": "S",
              "ascn_scon": 1301234,
              "modulus_19_suffix": "T"
             },
             "npsLgmpcalc":[{
                        "scheme_mem_start_date": "1978-04-06",
                       "scheme_end_date": "2010-05-10",
                        "revaluation_rate": 1,
                        "gmp_cod_allrate_tot": 7.88,
                        "gmp_cod_post_eightyeight_tot": 1.23,
                        "gmp_error_code": 0,
                        "reval_calc_switch_ind": 0

                }]
              }"""
        ).as[CalculationResponse]

        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockAppConfig.isHipEnabled).thenReturn(false)
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockIfConnector.calculate(any(), any())(any())).thenReturn(Future.successful(npsResponse))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(dualCalcCalculationRequest.copy(dualCalc = None)))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "dualCalc").as[JsBoolean].value must be(false)
      }
    }

    "when dual calculation with no cache with HIP connector" must {
      "return false" in {
        val hipCalculationResponse = Json.parse(
          """{
                "nationalInsuranceNumber": "AA000001A",
                "schemeContractedOutNumberDetails": "S2123456B",
                "payableAgeDate": "2022-06-27",
                "statePensionAgeDate": "2022-06-27",
                "dateOfDeath": "2022-06-27",
                "GuaranteedMinimumPensionDetailsList": [
                  {
                  "schemeMembershipStartDate": "2022-06-27",
                  "schemeMembershipEndDate": "2022-06-27",
                  "revaluationRate": "(NONE)",
                  "post1988GMPContractedOutDeductionsValue": 10.56,
                  "gmpContractedOutDeductionsAllRateValue": 10.56,
                  "gmpErrorCode": "Input revaluation date is before the termination date held on hmrc records",
                  "revaluationCalculationSwitchIndicator": true,
                  "post1990GMPContractedOutTrueSexTotal": 10.56,
                  "post1990GMPContractedOutOppositeSexTotal": 10.56,
                  "inflationProofBeyondDateofDeath": true,
                  "contributionsAndEarningsDetailsList": []
                  }
                ]
                }"""
        ).as[HipCalculationResponse]
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockHipConnector.calculate(any(), any())(any())).thenReturn(Future.successful(Success(hipCalculationResponse)))
        when(mockRepo.insertByRequest(any(), any())).thenReturn(Future.successful(true))
        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(dualCalcCalculationRequest.copy(dualCalc = None)))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "dualCalc").as[JsBoolean].value must be(false)
      }
    }

    "when date of death returned" must {
      "do this" in {
        val inputDate = LocalDate.parse("2016-01-01", fullDateFormatter)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(Some(calculationResponse.copy(dateOfDeath = Some(inputDate)))))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        (contentAsJson(result) \ "dateOfDeath").as[JsString].value must be("2016-01-01")
      }
    }

    "when date of death not returned" must {
      "do this" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(Some(calculationResponse)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)

        contentAsString(result) must not include "dateOfDeath"
      }
    }

    "when response audit causes exception" must {
      "log exception and return normally" in {
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.failed(new Exception()))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(Some(calculationResponse)))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")),
          body = Json.toJson(calculationRequest))

        val result = testCalculationController.requestCalculation("0000").apply(fakeRequest)

        contentAsString(result) must not include "dateOfDeath"
      }
    }

    "when citizens details return 423" must {
      "return a global error using DES connector" in {
        when(mockAppConfig.isIfsEnabled).thenReturn(false)
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
        when(mockRepo.findByRequest(any())).thenReturn(Future.successful(None))
        when(mockDesConnector.getPersonDetails(org.mockito.ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])).thenReturn(Future.successful(DesGetHiddenRecordResponse))

        val fakeRequest = FakeRequest(method = "POST", uri = "", headers = FakeHeaders(Seq("Content-type" -> "application/json")), body = Json.toJson(calculationRequest))
        val result = testCalculationController.requestCalculation("PSAID").apply(fakeRequest)
        val calcResponse = Json.fromJson[GmpCalculationResponse](contentAsJson(result)).get

        calcResponse.globalErrorCode must be(LOCKED)
      }
    }
  }

}
