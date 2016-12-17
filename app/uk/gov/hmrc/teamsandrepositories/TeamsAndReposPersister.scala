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
                                        time: LocalDateTime,
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


trait TeamsAndReposPersister {
  def add(teamsAndRepositories: PersistedTeamAndRepositories): Future[Boolean]

  def update(teamsAndRepositories: PersistedTeamAndRepositories): Future[Boolean]

  def allTeamsAndRepositories: Future[Map[String, Seq[PersistedTeamAndRepositories]]]

  def getAllTeamAndRepos: Future[Seq[PersistedTeamAndRepositories]]

  //!@  def getForService(serviceName: String): Future[Option[Seq[TeamAndRepos]]]

  def clearAllData: Future[Boolean]
}

@Singleton
case class MongoTeamsAndReposPersister @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[PersistedTeamAndRepositories, BSONObjectID](
    collectionName = "teamsAndRepositories",
    mongo = mongoConnector.db,
    domainFormat = PersistedTeamAndRepositories.formats) with TeamsAndReposPersister {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("teamName" -> IndexType.Hashed), name = Some("teamNameIdx")))
      )
    )


//  def upsert(teamAndRepos: PersistedTeamAndRepositories): Future[Boolean] = {
//    //!@
//    println(s"---> upserting: $teamAndRepos")
//    withTimerAndCounter("mongo.upsert") {
//      for {
//        _ <- collection.remove(query = Json.obj("teamName" -> Json.toJson(teamAndRepos.teamName)))
//        addResult <- add(teamAndRepos)
//      } yield addResult
//    }
//  }

  def update(teamAndRepos: PersistedTeamAndRepositories): Future[Boolean] = {
    //!@
    println(s"---> updating: $teamAndRepos")
    withTimerAndCounter("mongo.update") {
      for {
        update <- collection.update(selector = Json.obj("teamName" -> Json.toJson(teamAndRepos.teamName)), update = teamAndRepos, upsert = true)
      } yield update match {
        case lastError if lastError.inError => throw lastError
        case _ => true
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


  override def allTeamsAndRepositories: Future[Map[String, Seq[PersistedTeamAndRepositories]]] = findAll().map { all => all.groupBy(_.teamName) }

  //  def getForService(serviceName: String): Future[Option[Seq[TeamAndRepos]]] = {
  //
  //    withTimerAndCounter("mongo.read") {
  //      find("name" -> BSONDocument("$eq" -> serviceName)) map {
  //        case Nil => None
  //        case data => Some(data.sortBy(_.productionDate.toEpochSecond(ZoneOffset.UTC)).reverse)
  //      }
  //    }
  //  }

  def clearAllData = super.removeAll().map(!_.hasErrors)

  def getAllTeamAndRepos: Future[Seq[PersistedTeamAndRepositories]] = collection
    .find(BSONDocument.empty)
    //    .sort(Json.obj("teamName" -> JsNumber(-1)))
    .cursor[PersistedTeamAndRepositories]()
    .collect[List]()

}



