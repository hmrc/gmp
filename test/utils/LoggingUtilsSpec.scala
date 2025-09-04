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

package utils

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class LoggingUtilsSpec extends AnyWordSpec with Matchers {
  
  "LoggingUtils" should {
    
    "redactSensitive" should {
      "return redacted value for null" in {
        LoggingUtils.redactSensitive(null) shouldBe "[REDACTED]"
      }
      
      "return all asterisks for empty string" in {
        LoggingUtils.redactSensitive("") shouldBe ""
      }
      
      "return all asterisks for string of length 3 or less" in {
        LoggingUtils.redactSensitive("A") shouldBe "*"
        LoggingUtils.redactSensitive("AB") shouldBe "**"
        LoggingUtils.redactSensitive("ABC") shouldBe "***"
      }
      
      "show first 3 characters and redact the rest for longer strings" in {
        LoggingUtils.redactSensitive("ABCD") shouldBe "ABC*"
        LoggingUtils.redactSensitive("ABCDE") shouldBe "ABC**"
        LoggingUtils.redactSensitive("S1234567T") shouldBe "S12******"
      }
      
      "not fail with special characters" in {
        LoggingUtils.redactSensitive("A@B#C$") shouldBe "A@B***"
      }
    }
    
    "redactError" should {
      "return empty string for null" in {
        LoggingUtils.redactError(null) shouldBe ""
      }
      
      "redact numbers" in {
        LoggingUtils.redactError("Error 12345") shouldBe "Error *****"
      }
      
      "redact email addresses" in {
        val email = "user@example.com"
        LoggingUtils.redactError(s"Contact $email for help") shouldBe "Contact [email] for help"
      }
      
      "truncate long error messages" in {
        val longMessage = "A" * 200
        LoggingUtils.redactError(longMessage).length shouldBe 100
      }
      
      "handle multiple redactions in same string" in {
        val input = "Error 123 for user@example.com and 456 for another@test.com"
        val expected = "Error *** for [email] and *** for [email]"
        LoggingUtils.redactError(input) shouldBe expected
      }
    }
  }
}
