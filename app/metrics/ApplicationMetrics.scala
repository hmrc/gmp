/*
 * Copyright 2023 HM Revenue & Customs
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
import com.codahale.metrics.MetricRegistry
import com.google.inject.Inject
import com.kenshoo.play.metrics.Metrics
import play.api.Logging

import scala.util.Try

class ApplicationMetrics @Inject()(metrics: Metrics) extends Logging {
  lazy val registry: MetricRegistry = metrics.defaultRegistry

  private val timer = (name: String) => Try{registry.timer(name)}
  private val counter = (name: String) => Try{registry.counter(name)}

  logger.info("[Metrics][constructor] Preloading metrics keys")

  Seq(
    ("nps-connector-timer", timer),
    ("nps-connector-status-200", counter),
    ("nps-connector-status-400", counter),
    ("nps-connector-status-500", counter),
    ("if-connector-timer", timer),
    ("if-connector-status-200", counter),
    ("if-connector-status-400", counter),
    ("if-connector-status-500", counter),
    ("mci-connection-timer", timer),
    ("mci-lock-result-count", counter),
    ("mci-error-count", counter)
  ) foreach { t => t._2(t._1) }

  def desConnectorTimer(diff: Long, unit: TimeUnit): Unit = Try{registry.timer("nps-connector-timer").update(diff, unit)}
    .failed.foreach(ex => "nps-connector-timer failed: metrics might be disabled")
  def desConnectorStatus(code: Int): Unit = Try{registry.counter(s"nps-connector-status-$code").inc()}
    .failed.foreach(ex => "nps-connector-status failed: metrics might be disabled")

  def ifConnectorTimer(diff: Long, unit: TimeUnit): Unit = Try {
    registry.timer("if-connector-timer").update(diff, unit)
  }
    .failed.foreach(ex => "ifs-connector-timer failed: metrics might be disabled")

  def ifConnectorStatus(code: Int): Unit = Try {
    registry.counter(s"if-connector-status-$code").inc()
  }
    .failed.foreach(ex => "if-connector-status failed: metrics might be disabled")
  def mciConnectionTimer(diff: Long, unit: TimeUnit): Unit = Try{registry.timer("mci-connection-timer").update(diff, unit)}
    .failed.foreach(ex => "mci-connection-timer failed: metrics might be disabled")
  def mciLockCount(): Unit = Try{registry.counter("mci-lock-result-count").inc()}
    .failed.foreach(ex => "mci-lock-result-count failed: metrics might be disabled")
  def mciErrorCount(): Unit = Try{registry.counter("mci-error-count").inc()}
    .failed.foreach(ex => "mci-error-count failed: metrics might be disabled")
}
