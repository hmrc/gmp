/*
 * Copyright 2016 HM Revenue & Customs
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

import com.kenshoo.play.metrics.MetricsRegistry

trait Metrics {
  def desConnectorTimer(diff: Long, unit: TimeUnit): Unit
  def desConnectorStatus(code: Int): Unit
  def mciConnectionTimer(diff: Long, unit: TimeUnit): Unit
  def mciLockCount(): Unit
  def mciErrorCount(): Unit
}

object Metrics extends Metrics {
  override def desConnectorTimer(diff: Long, unit: TimeUnit) = MetricsRegistry.defaultRegistry.timer("nps-connector-timer").update(diff, unit)
  override def desConnectorStatus(code: Int) = MetricsRegistry.defaultRegistry.counter(s"nps-connector-status-$code").inc()
  override def mciConnectionTimer(diff: Long, unit: TimeUnit) = MetricsRegistry.defaultRegistry.timer("mci-connection-timer").update(diff, unit)
  override def mciLockCount() = MetricsRegistry.defaultRegistry.counter("mci-lock-result-count").inc()
  override def mciErrorCount() = MetricsRegistry.defaultRegistry.counter("mci-error-count").inc()
}
