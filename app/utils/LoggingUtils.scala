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
  
  /**
   * Redacts sensitive information from calculation requests and responses
   * @param json The JSON string containing calculation data
   * @return Redacted JSON string with sensitive information removed
   */
  def redactCalculationData(json: String): String = {
    import play.api.libs.json._
    
    def redactField(js: JsValue, path: JsPath): JsValue = {
      js.transform(path.json.prune).getOrElse(js) match {
        case JsObject(fields) => 
          JsObject(fields.map { case (k, v) => 
            k -> (v match {
              case JsString(s) if k.toLowerCase.contains("nino") || k.equalsIgnoreCase("scon") => 
                JsString(redactSensitive(s))
              case JsString(s) if k.toLowerCase.contains("name") || k.toLowerCase.contains("surname") => 
                JsString(if (s.length > 3) s.take(1) + "*" * (s.length - 1) else "*" * s.length)
              case o: JsObject => redactField(o, path)
              case a: JsArray => 
                JsArray(a.value.map(redactField(_, path)))
              case _ => v
            })
          })
        case a: JsArray => 
          JsArray(a.value.map(redactField(_, path)))
        case other => other
      }
    }
    
    try {
      Json.parse(json) match {
        case obj: JsObject => 
          val redacted = redactField(obj, __)
          Json.prettyPrint(redacted)
        case arr: JsArray =>
          val redacted = redactField(arr, __)
          Json.prettyPrint(redacted)
        case _ => json
      }
    } catch {
      case _: Throwable => 
        // If JSON parsing fails, return a redacted version of the string
        redactError(json)
    }
  }
  
}
