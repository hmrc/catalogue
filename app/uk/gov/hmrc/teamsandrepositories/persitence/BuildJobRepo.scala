/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.persitence

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.teamsandrepositories.persitence.model.BuildJob
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import reactivemongo.api.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BuildJobRepo @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[BuildJob, BSONObjectID] (
    collectionName = "jenkinsLinks",
    mongo          = mongoConnector.db,
    domainFormat   = BuildJob.mongoFormats) {

  override def indexes: Seq[Index.Default] =
    Seq(
      Index(
        key = Seq("service" -> IndexType.Hashed),
        name = Some("serviceIdx"),
        unique = false,
        background = false,
        sparse = false,
        expireAfterSeconds = None,
        storageEngine = None,
        weights = None,
        defaultLanguage = None,
        languageOverride = None,
        textIndexVersion = None,
        sphereIndexVersion = None,
        bits = None,
        min = None,
        max = None,
        bucketSize = None,
        collation = None,
        wildcardProjection = None,
        version = None,
        partialFilter = None,
        options = BSONDocument.empty
      )

    )

  def findByService(service: String)(implicit ec: ExecutionContext): Future[Option[BuildJob]] =
    find("service" -> service)
      .map(_.headOption)

  def updateOne(buildJob: BuildJob)(implicit ec: ExecutionContext): Future[UpdateWriteResult] = {
    collection
      .update(ordered=false)
      .one(
        q      = Json.obj("service" -> buildJob.service),
        u      = Json.obj("$set" -> Json.obj("jenkinsURL" -> buildJob.jenkinsURL)),
        upsert = true
      )
  }

  def update(buildJobs: Seq[BuildJob])(implicit ec: ExecutionContext): Future[Seq[UpdateWriteResult]] =
    Future.traverse(buildJobs)(updateOne)
}
