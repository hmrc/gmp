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


object HipErrorCodeMapper {

  private final val DefaultErrorCode = 0

  private final val rejectionReasonMap: Map[String, Int] = Map(
    "No match for person details provided" -> 63119, //used in stub
    "GMP Calculation not possible" -> 63120, //used in stub
    "Date of birth not held" -> 63121 //used in stub
  )
  private final val gmpErrorCodeMap: Map[String, Int] = Map(
    "Earnings in excess" -> 56004, //used in stub
    "Earnings erroneous" -> 56003, //used in stub,
    "More than one scheme with the same ECON - no control earnings" -> 56021,
    "No pre 1997 liability held for transfer chain" -> 56067
  )

  def mapRejectionReason(rejectionReason: String): Int =
    Option(rejectionReason).filter(_.nonEmpty).flatMap(rejectionReasonMap.get).getOrElse(DefaultErrorCode)

  def mapGmpErrorCode(gmpErrorCode: String): Int =
    Option(gmpErrorCode).filter(_.nonEmpty).flatMap(gmpErrorCodeMap.get).getOrElse(DefaultErrorCode)

}