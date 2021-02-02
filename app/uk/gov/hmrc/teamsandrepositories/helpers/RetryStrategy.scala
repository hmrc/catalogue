/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.helpers

import java.util.{Timer, TimerTask}

import play.api.Logger
import uk.gov.hmrc.githubclient.APIRateLimitExceededException

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.{Duration, DurationInt}

object RetryStrategy {

  private val logger = Logger(this.getClass)

  private def delay[T](delay: Duration)(eventualT: => Future[T]): Future[T] = {
    val promise = Promise[T]()
    new Timer().schedule(new TimerTask {
      override def run() = promise.completeWith(eventualT)
    }, delay.toMillis)

    promise.future
  }

  def exponentialRetry[T](times: Int, duration: Duration = 10.millis)(f: => Future[T])(
    implicit executor: ExecutionContext): Future[T] =
    f.recoverWith {
      case e: APIRateLimitExceededException =>
        logger.error(s"API rate limit is reached (at retry:$times)", e)
        Future.failed(e)

      case e if times > 0 =>
        logger.error("error making request Retrying :", e)
        logger.debug(s"Retrying with delay $duration attempts remaining: ${times - 1}")
        delay(duration) {
          exponentialRetry(times - 1, duration * 2)(f)
        }
    }
}
