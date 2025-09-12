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
import play.api.libs.json._

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
    
    "redactCalculationData" should {
      "handle null input" in {
        LoggingUtils.redactCalculationData(null) shouldBe ""
      }
      
      "handle empty string input" in {
        LoggingUtils.redactCalculationData("") shouldBe ""
      }
      
      "handle invalid JSON input" in {
        val invalidJson = "{invalid: json}"
        // The method should return the input as-is for invalid JSON
        LoggingUtils.redactCalculationData(invalidJson) shouldBe invalidJson
      }
      
      "redact NINO in JSON object" in {
        val json = """{"nino": "AB123456C", "otherField": "value"}"""
        val result = LoggingUtils.redactCalculationData(json)
        result should include("AB1******")
        result should include("otherField")
        result should include("value")
      }
      
      "redact SCON in JSON object" in {
        val json = """{"scon": "S1234567T", "details": "test"}"""
        val result = LoggingUtils.redactCalculationData(json)
        result should include("S12******")
        result should include("details")
      }
      
      "redact name fields in JSON object" in {
        val json = """{"firstName": "John", "surname": "Doe", "fullName": "John Doe"}"""
        val result = LoggingUtils.redactCalculationData(json)
        result should include("J***")
        result should include("***") // surname is fully redacted
        result should include("J*******") // fullName redaction
      }
      
      "handle nested JSON objects" in {
        val json = """{"user": {"nino": "AB123456C", "name": "John"}, "other": "data"}"""
        val result = LoggingUtils.redactCalculationData(json)
        result should include("AB1******")
        result should include("J***")
        result should include("data")
      }
      
      "handle JSON arrays" in {
        val json = """[{"nino": "AB123456C"}, {"scon": "S1234567T"}]"""
        val result = LoggingUtils.redactCalculationData(json)
        result should include("AB1******")
        result should include("S12******")
      }
      
      "not modify non-sensitive fields" in {
        val json = """{"id": 123, "active": true, "amount": 100.50}"""
        val result = LoggingUtils.redactCalculationData(json)
        result should include("\"id\" : 123")
        result should include("\"active\" : true")
        result should include("\"amount\" : 100.5")
      }
      
      "handle complex nested structures" in {
        val json = """
          |{
          |  "user": {
          |    "personalDetails": {
          |      "firstName": "John",
          |      "lastName": "Doe",
          |      "nino": "AB123456C"
          |    },
          |    "contacts": [
          |      {"type": "email", "value": "john@example.com"},
          |      {"type": "phone", "value": "1234567890"}
          |    ]
          |  },
          |  "scon": "S1234567T",
          |  "amount": 1000.50
          |}
        """.stripMargin
        
        val result = LoggingUtils.redactCalculationData(json)
        
        // Check redaction of sensitive fields
        result should include("J***") // First name first character + ***
        result should include("***")  // Last name fully redacted
        result should include("AB1******") // NINO redaction
        result should include("S12******") // SCON redaction
        
        // Check non-sensitive fields are present
        result should include("personalDetails")
        result should include("contacts")
        result should include("\"type\" : \"email\"")
        result should include("\"type\" : \"phone\"")
        result should include("\"amount\" : 1000.5")
      }
    }
  }
}
