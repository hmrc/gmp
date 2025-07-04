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

package utils


import base.BaseSpec
import utils.HipErrorCodeMapper

class HipErrorCodeMapperSpec extends BaseSpec {

  "HipErrorCodeMapper.mapRejectionReason" should {
    "return correct int for known rejection reason" in {
      HipErrorCodeMapper.mapRejectionReason("No match for person details provided") mustBe 63119
      HipErrorCodeMapper.mapRejectionReason("GMP Calculation not possible") mustBe 63120
      HipErrorCodeMapper.mapRejectionReason("Date of birth not held") mustBe 63121
    }
    "return 0 for unknown rejection reason" in {
      HipErrorCodeMapper.mapRejectionReason("Invalid Person") mustBe 0
    }
    "return 0 for empty string" in {
      HipErrorCodeMapper.mapRejectionReason("") mustBe 0
    }
    "return 0 for null" in {
      HipErrorCodeMapper.mapRejectionReason(null) mustBe 0
    }
  }
  "HipErrorCodeMapper.mapGmpErrorCode" should {
    "return correct int for known GMP error code" in {
      HipErrorCodeMapper.mapGmpErrorCode("Earnings in excess") mustBe 56004
      HipErrorCodeMapper.mapGmpErrorCode("Earnings erroneous") mustBe 56003
      HipErrorCodeMapper.mapGmpErrorCode("More than one scheme with the same ECON - no control earnings") mustBe 56021
      HipErrorCodeMapper.mapGmpErrorCode("No pre 1997 liability held for transfer chain") mustBe 56067
    }
    "return 0 for unknown GMP error code" in {
      HipErrorCodeMapper.mapGmpErrorCode("Unknown error code") mustBe 0
    }
    "return 0 for empty string" in {
      HipErrorCodeMapper.mapGmpErrorCode("") mustBe 0
    }
    "return 0 for null" in {
      HipErrorCodeMapper.mapGmpErrorCode(null) mustBe 0

    }
  }
}