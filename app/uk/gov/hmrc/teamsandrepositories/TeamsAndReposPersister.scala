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

package uk.gov.hmrc.teamsandrepositories

import java.time.{LocalDateTime, ZoneOffset}

import com.google.inject.{Inject, Singleton}
import play.api.libs.json._
import reactivemongo.api.DB
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.teamsandrepositories.FutureHelpers.withTimerAndCounter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


case class PersistedTeamAndRepositories(teamName: String,
                                        repositories: List[GitRepository])

object PersistedTeamAndRepositories {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  //  implicit val bsonIdFormat = ReactiveMongoFormats.objectIdFormats
  //  val gitRepositoryFormat = Json.format[Seq[GitRepository]]
  implicit val formats = Json.format[PersistedTeamAndRepositories]

}

//!@ test this
class TeamsAndReposPersister @Inject()(mongoTeamsAndReposPersister: MongoTeamsAndReposPersister, mongoUpdateTimePersister: MongoUpdateTimePersister) {

  val teamsAndRepositoriesTimestampKeyName = "teamsAndRepositories.updated"

  def add(teamsAndRepositories: PersistedTeamAndRepositories): Future[Boolean] = {
    mongoTeamsAndReposPersister.add(teamsAndRepositories)
  }

  def update(teamsAndRepositories: PersistedTeamAndRepositories): Future[PersistedTeamAndRepositories] = {
    mongoTeamsAndReposPersister.update(teamsAndRepositories)
  }

  def getAllTeamAndReposOld: Future[Seq[PersistedTeamAndRepositories]] = {
    mongoTeamsAndReposPersister.getAllTeamAndRepos0
  }

  def getAllTeamAndRepos: Future[(Seq[PersistedTeamAndRepositories], Option[LocalDateTime])] = {
    for {
      teamsAndRepos <- mongoTeamsAndReposPersister.getAllTeamAndRepos0
      timestamp <- mongoUpdateTimePersister.get(teamsAndRepositoriesTimestampKeyName)
    } yield (teamsAndRepos, timestamp.map(_.timestamp))

  }


//  def getAllTeamAndRepos: Future[Seq[PersistedTeamAndRepositories]] = {
//    mongoTeamsAndReposPersister.getAllTeamAndRepos
//  }

  def clearAllData: Future[Boolean] = {
    mongoTeamsAndReposPersister.clearAllData
    mongoUpdateTimePersister.remove(teamsAndRepositoriesTimestampKeyName)
  }

  def updateTimestamp(timestamp: LocalDateTime) = {
    mongoUpdateTimePersister.update(KeyAndTimestamp(teamsAndRepositoriesTimestampKeyName, timestamp))
  }

}

@Singleton
case class MongoTeamsAndReposPersister @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[PersistedTeamAndRepositories, BSONObjectID](
    collectionName = "teamsAndRepositories",
    mongo = mongoConnector.db,
    domainFormat = PersistedTeamAndRepositories.formats) {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("teamName" -> IndexType.Hashed), name = Some("teamNameIdx")))
      )
    )


  def update(teamAndRepos: PersistedTeamAndRepositories): Future[PersistedTeamAndRepositories] = {

    withTimerAndCounter("mongo.update") {
      for {
        update <- collection.update(selector = Json.obj("teamName" -> Json.toJson(teamAndRepos.teamName)), update = teamAndRepos, upsert = true)
      } yield update match {
        case lastError if lastError.inError => throw new RuntimeException(s"failed to persist $teamAndRepos")
        case _ => teamAndRepos
      }
    }
  }


  def add(teamsAndRepository: PersistedTeamAndRepositories): Future[Boolean] = {
    withTimerAndCounter("mongo.write") {
      insert(teamsAndRepository) map {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    }
  }


  def getAllTeamAndRepos0: Future[Seq[PersistedTeamAndRepositories]] = findAll()

  def clearAllData = super.removeAll().map(!_.hasErrors)

//  def getAllTeamAndRepos: Future[Seq[PersistedTeamAndRepositories]] = collection
//    .find(BSONDocument.empty)
//    .cursor[PersistedTeamAndRepositories]()
//    .collect[List]()

}

case class KeyAndTimestamp(keyName: String, timestamp: LocalDateTime)

object KeyAndTimestamp {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val formats = Json.format[KeyAndTimestamp]
}


@Singleton
case class MongoUpdateTimePersister @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[PersistedTeamAndRepositories, BSONObjectID](
    collectionName = "updateTime",
    mongo = mongoConnector.db,
    domainFormat = PersistedTeamAndRepositories.formats) {

  private val keyFieldName = "keyName"

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq(keyFieldName -> IndexType.Hashed), name = Some(keyFieldName + "Idx")))
      )
    )

  def get(keyName: String): Future[Option[KeyAndTimestamp]] = {
    withTimerAndCounter("mongo.timestamp.get") {
      collection.find(Json.obj(keyFieldName -> Json.toJson(keyName)))
        .cursor[KeyAndTimestamp]()
        .collect[List]().map(_.headOption)
    }
  }

  def update(keyAndTimestamp: KeyAndTimestamp): Future[Boolean] = {
    withTimerAndCounter("mongo.timestamp.update") {
      for {
        update <- collection.update(selector = Json.obj(keyFieldName -> Json.toJson(keyAndTimestamp.keyName)), update = keyAndTimestamp, upsert = true)
      } yield update match {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    }
  }

  def remove(keyName: String): Future[Boolean] = super.remove(keyFieldName -> Json.toJson(keyName)).map(!_.hasErrors)
}


