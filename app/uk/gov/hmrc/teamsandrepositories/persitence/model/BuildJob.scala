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

package uk.gov.hmrc.teamsandrepositories.persitence.model

import play.api.libs.json._

case class BuildJob(service: String, jenkinsURL: String)

object BuildJob {
  val mongoFormats: OFormat[BuildJob] =
    Json.format[BuildJob]

  val apiWriter: Writes[BuildJob] = new Writes[BuildJob] {
    override def writes(o: BuildJob): JsValue = Json.obj("service" -> o.service, "jenkinsURL" -> o.jenkinsURL)
  }

}
