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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class HipFailure(reason: String, code: Option[String], failureType: Option[String])

object HipFailure {
  private def parseCode(raw: Option[JsValue]): Option[String] = raw.flatMap {
    case JsNumber(n) => Some(n.toString())
    case JsString(s) =>
      val trimmed = s.trim
      if (trimmed.matches("""\d+(\.\d+)?""")) Some(trimmed) else Some("0")
    case _ => Some("0")
  }

  private def parseType(raw: Option[String]): Option[String] =
    raw.map(_.trim).filter(_.nonEmpty)

  implicit val reads: Reads[HipFailure] = (
    (JsPath \ "reason").read[String] and
      (JsPath \ "code").readNullable[JsValue].map(parseCode) and
      (JsPath \ "type").readNullable[String].map(parseType)
    )(HipFailure.apply _)

  implicit val writes: OWrites[HipFailure] = OWrites[HipFailure] { failure =>
    val base = Json.obj("reason" -> failure.reason)
    val withCode = failure.code.map(code => base + ("code" -> JsString(code))).getOrElse(base)
    failure.failureType.map(t => withCode + ("type" -> JsString(t))).getOrElse(withCode)
  }
}

case class HipCalculationFailuresResponse(origin: Option[String] = None, failures: List[HipFailure])

object HipCalculationFailuresResponse {
  private val directReads: Reads[HipCalculationFailuresResponse] =
    (
      (JsPath \ "origin").readNullable[String] and
        (JsPath \ "failures").read[List[HipFailure]]
      )(HipCalculationFailuresResponse.apply _)

  private val nestedReads: Reads[HipCalculationFailuresResponse] =
    (
      (JsPath \ "origin").readNullable[String] and
        (JsPath \ "response" \ "failures").read[List[HipFailure]]
      )(HipCalculationFailuresResponse.apply _)

  private val singleFailureReads: Reads[HipCalculationFailuresResponse] =
    (
      (JsPath \ "origin").readNullable[String] and
        HipFailure.reads
      )((origin, failure) => HipCalculationFailuresResponse(origin, List(failure)))

  implicit val reads: Reads[HipCalculationFailuresResponse] =
    directReads orElse nestedReads orElse singleFailureReads

  implicit val writes: OWrites[HipCalculationFailuresResponse] =
    OWrites[HipCalculationFailuresResponse] { value =>
      val base = Json.obj("failures" -> value.failures)
      value.origin.map(o => base + ("origin" -> JsString(o))).getOrElse(base)
    }
}
