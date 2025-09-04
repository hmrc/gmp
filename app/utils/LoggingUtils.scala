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

object LoggingUtils {
  private val RedactedValue = "[REDACTED]"
  
  /**
   * Redacts sensitive information from strings before logging
   * @param value The value to be redacted
   * @return Redacted value (first 3 characters shown, rest redacted if longer than 3)
   */
  def redactSensitive(value: String): String = {
    if (value == null) {
      RedactedValue
    } else if (value.length <= 3) {
      "*" * value.length
    } else {
      value.take(3) + "*" * (value.length - 3)
    }
  }
  
  /**
   * Redacts sensitive information from error messages
   * @param error The error message to be redacted
   * @return Redacted error message with sensitive information removed
   */
  def redactError(error: String): String = {
    if (error == null) ""
    else {
      // Redact any potential sensitive information from error messages
      error
        .replaceAll("([0-9])", "*")
        .replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[email]")
        .take(100) // Limit error message length
    }
  }
}
