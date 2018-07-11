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

package uk.gov.hmrc.teamsandrepositories.persitence

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONObjectID, BSONRegex}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TeamsAndReposPersister @Inject()(
  mongoTeamsAndReposPersister: MongoTeamsAndRepositoriesPersister,
  futureHelpers: FutureHelpers) {
  val teamsAndRepositoriesTimestampKeyName = "teamsAndRepositories.updated"

  def update(teamsAndRepositories: TeamRepositories): Future[TeamRepositories] = {
    Logger.info(
      s"Updating team record: ${teamsAndRepositories.teamName} (${teamsAndRepositories.repositories.size} repos)")
    mongoTeamsAndReposPersister.update(teamsAndRepositories)
  }

  def getAllTeamsAndRepos: Future[Seq[TeamRepositories]] =
    mongoTeamsAndReposPersister.getAllTeamAndRepos

  def getTeamsAndRepos(serviceNames: Seq[String]): Future[Seq[TeamRepositories]] =
    mongoTeamsAndReposPersister.getTeamsAndRepos(serviceNames)

  def clearAllData: Future[Boolean] =
    mongoTeamsAndReposPersister.clearAllData

  def deleteTeams(teamNames: Set[String]): Future[Set[String]] = {
    Logger.debug(s"Deleting orphan teams: $teamNames")
    Future.sequence(teamNames.map(mongoTeamsAndReposPersister.deleteTeam))
  }

  def deleteRepo(repoName: String): Future[Option[String]] = {
    Logger.debug(s"Deleting repo: $repoName")
    mongoTeamsAndReposPersister.deleteRepo(repoName)
  }
}

@Singleton
class MongoTeamsAndRepositoriesPersister @Inject()(mongoConnector: MongoConnector, futureHelpers: FutureHelpers)
    extends ReactiveRepository[TeamRepositories, BSONObjectID](
      collectionName = "teamsAndRepositories",
      mongo          = mongoConnector.db,
      domainFormat   = TeamRepositories.formats) {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("teamName" -> IndexType.Hashed), name = Some("teamNameIdx")))
      )
    )

  def update(teamAndRepos: TeamRepositories): Future[TeamRepositories] =
    futureHelpers.withTimerAndCounter("mongo.update") {
      for {
        update <- collection.update(
                   selector = Json.obj("teamName" -> Json.toJson(teamAndRepos.teamName)),
                   update   = teamAndRepos,
                   upsert   = true)
      } yield
        update match {
          case _ =>
            teamAndRepos
        }
    } recover {
      case lastError =>
        throw new RuntimeException(s"failed to persist $teamAndRepos", lastError)
    }

  def getAllTeamAndRepos: Future[List[TeamRepositories]] = findAll()

  def getTeamsAndRepos(serviceNames: Seq[String]): Future[List[TeamRepositories]] = {
    val serviceNamesJson =
      serviceNames.map(serviceName =>
        toJsFieldJsValueWrapper(Json.obj("name" -> BSONRegex("^" + serviceName + "$", "i"))))
    find("repositories" -> Json.obj("$elemMatch" -> Json.obj("$or" -> Json.arr(serviceNamesJson: _*))))
  }

  def clearAllData: Future[Boolean] = super.removeAll().map(_.ok)

  def deleteTeam(teamName: String): Future[String] =
    futureHelpers.withTimerAndCounter("mongo.cleanup") {
      collection.remove(selector = Json.obj("teamName" -> Json.toJson(teamName))).map(_ => teamName)
    } recover {
      case lastError =>
        logger.error(s"Failed to remove $teamName", lastError)
        throw new RuntimeException(s"failed to remove $teamName")
    }

  def deleteRepo(repoName: String): Future[Option[String]] =
    collection.update(
      selector = Json.obj(),
      update   = Json.obj("$pull" -> Json.obj("repositories" -> Json.obj("name" -> repoName))),
      multi    = true
    ) map { result =>
      result.nModified match {
        case 0     => None
        case 1     => Some(repoName)
        case other => throw new RuntimeException(s"Removed $other number of '$repoName' repositories.")
      }
    }
}
