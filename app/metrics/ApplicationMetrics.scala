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

package metrics

import java.util.concurrent.TimeUnit
import com.codahale.metrics.MetricRegistry
import com.google.inject.Inject
import play.api.Logging

import scala.util.Try

class ApplicationMetrics @Inject()(registry: MetricRegistry) extends Logging {

  private val timer = (name: String) => Try{registry.timer(name)}
  private val counter = (name: String) => Try{registry.counter(name)}

  logger.info("[Metrics][constructor] Preloading metrics keys")

  Seq(
    ("nps-connector-timer", timer),
    ("nps-connector-status-200", counter),
    ("nps-connector-status-400", counter),
    ("nps-connector-status-500", counter),
    ("hip-connector-timer", timer),
    ("hip-connector-status-200", counter),
    ("hip-connector-status-400", counter),
    ("hip-connector-status-403", counter),
    ("hip-connector-status-500", counter),
    ("hip-connector-status-503", counter),
    ("if-connector-timer", timer),
    ("if-connector-status-200", counter),
    ("if-connector-status-400", counter),
    ("if-connector-status-500", counter),
    ("mci-connection-timer", timer),
    ("mci-lock-result-count", counter),
    ("mci-error-count", counter)
  ) foreach { t => t._2(t._1) }

  private def recordMetric(metricName: String, action: => Unit): Unit = {
    Try(action).failed.foreach { ex =>
      logger.warn(s"$metricName failed: ${ex.getMessage}")
    }
  }

  // NPS (DES) metrics
  def desConnectorTimer(diff: Long, unit: TimeUnit): Unit = {
    recordMetric("nps-connector-timer", {
      registry.timer("nps-connector-timer").update(diff, unit)
    })
  }

  def desConnectorStatus(code: Int): Unit = {
    recordMetric(s"nps-connector-status-$code", {
      registry.counter(s"nps-connector-status-$code").inc()
    })
  }

  // HIP metrics
  def hipConnectorTimer(diff: Long, unit: TimeUnit): Unit = {
    recordMetric("hip-connector-timer", {
      registry.timer("hip-connector-timer").update(diff, unit)
    })
  }

  def hipConnectorStatus(code: Int): Unit = {
    recordMetric(s"hip-connector-status-$code", {
      registry.counter(s"hip-connector-status-$code").inc()
    })
  }

  // IF metrics
  def ifConnectorTimer(diff: Long, unit: TimeUnit): Unit = {
    recordMetric("if-connector-timer", {
      registry.timer("if-connector-timer").update(diff, unit)
    })
  }

  def ifConnectorStatus(code: Int): Unit = {
    recordMetric(s"if-connector-status-$code", {
      registry.counter(s"if-connector-status-$code").inc()
    })
  }

  // MCI metrics
  def mciConnectionTimer(diff: Long, unit: TimeUnit): Unit = {
    recordMetric("mci-connection-timer", {
      registry.timer("mci-connection-timer").update(diff, unit)
    })
  }

  def mciLockCount(): Unit = {
    recordMetric("mci-lock-result-count", {
      registry.counter("mci-lock-result-count").inc()
    })
  }

  def mciErrorCount(): Unit = {
    recordMetric("mci-error-count", {
      registry.counter("mci-error-count").inc()
    })
  }
}
