/*
 * Copyright 2019 HM Revenue & Customs
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

package metrics

import java.util.concurrent.TimeUnit

import com.google.inject.Inject
import com.kenshoo.play.metrics.Metrics
import play.api.Logger

class ApplicationMetrics @Inject()(metrics: Metrics) {
  lazy val registry = metrics.defaultRegistry

  private val timer = (name: String) => registry.timer(name)
  private val counter = (name: String) => registry.counter(name)

  Logger.info("[Metrics][constructor] Preloading metrics keys")

  Seq(
    ("nps-connector-timer", timer),
    ("nps-connector-status-200", counter),
    ("nps-connector-status-400", counter),
    ("nps-connector-status-500", counter),
    ("mci-connection-timer", timer),
    ("mci-lock-result-count", counter),
    ("mci-error-count", counter)
  ) foreach { t => t._2(t._1) }

  def desConnectorTimer(diff: Long, unit: TimeUnit) = registry.timer("nps-connector-timer").update(diff, unit)
  def desConnectorStatus(code: Int) = registry.counter(s"nps-connector-status-$code").inc()
  def mciConnectionTimer(diff: Long, unit: TimeUnit) = registry.timer("mci-connection-timer").update(diff, unit)
  def mciLockCount() = registry.counter("mci-lock-result-count").inc()
  def mciErrorCount() = registry.counter("mci-error-count").inc()
}
