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

package uk.gov.hmrc.teamsandrepositories.services

import java.time.Instant
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persistence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.connectors.{GhTeam, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.util.DateTimeUtils
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class Timestamper {
  def timestampF() = Instant.now()
}

@Singleton
case class PersistingService @Inject()(
  githubConfig    : GithubConfig,
  persister       : TeamsAndReposPersister,
  githubConnector : GithubConnector,
  timestamper     : Timestamper,
  configuration   : Configuration
) {
  private val logger = Logger(this.getClass)

  val sharedRepos: List[String] =
    configuration.get[Seq[String]]("shared.repositories").toList

  val dataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(
      githubConfig    = githubConfig,
      githubConnector = githubConnector,
      timestampF      = timestamper.timestampF,
      sharedRepos     = sharedRepos
    )

  def persistTeamRepoMapping(implicit ec: ExecutionContext): Future[Seq[TeamRepositories]] =
    (for {
       persistedTeams <- persister.getAllTeamsAndRepos(None)
       sortedGhTeams  <- teamsOrderedByUpdateDate(persistedTeams)
       withTeams      <- sortedGhTeams.foldLeftM(Seq.empty[TeamRepositories]){ case (acc, ghTeam) =>
                           dataSource
                             .mapTeam(ghTeam, persistedTeams)
                             .map(tr => tr.copy(repositories = tr.repositories.sortBy(_.name)))
                             .flatMap(persister.update)
                             .map(acc :+ _)
                         }
       withoutTeams   <- getRepositoriesWithoutTeams(withTeams).flatMap(persister.update)
     } yield withTeams :+ withoutTeams
    ).recoverWith {
      case NonFatal(ex) =>
        logger.error("Could not persist to teams repo.", ex)
        Future.failed(ex)
    }

  def getRepositoriesWithoutTeams(
      persistedReposWithTeams: Seq[TeamRepositories]
    )( implicit ec: ExecutionContext
    ): Future[TeamRepositories] =
    dataSource.getAllRepositories
      .map { repos =>
        val reposWithoutTeams = {
          val urlsOfPersistedRepos = persistedReposWithTeams.flatMap(_.repositories.map(_.url)).toSet
          repos.filterNot(r => urlsOfPersistedRepos.contains(r.url))
        }
        TeamRepositories(
          teamName     = TeamRepositories.TEAM_UNKNOWN,
          repositories = reposWithoutTeams,
          createdDate  = None,
          updateDate   = timestamper.timestampF()
        )
      }

  private implicit val io: Ordering[Instant] = DateTimeUtils.instantOrdering

  private def teamsOrderedByUpdateDate(
      persistedTeams: Seq[TeamRepositories]
    )( implicit ec: ExecutionContext
    ): Future[List[GhTeam]] =
      dataSource.getTeams
        .map(
          _.sortBy(ghTeam => persistedTeams.find(_.teamName == ghTeam.name).fold(Instant.MIN)(_.updateDate))
        )

  def removeOrphanTeamsFromMongo(
       teamRepositoriesFromGh: Seq[TeamRepositories]
    )( implicit ec: ExecutionContext
    ): Future[Set[String]] =
    (for {
       mongoTeams      <- persister.getAllTeamsAndRepos(None).map(_.map(_.teamName).toSet)
       teamNamesFromGh =  teamRepositoriesFromGh.map(_.teamName)
       orphanTeams     =  mongoTeams.filterNot(teamNamesFromGh.toSet)
       _               =  logger.info(s"Removing these orphan teams:[$orphanTeams]")
       deleted         <- persister.deleteTeams(orphanTeams)
     } yield deleted
    ).recover {
      case e =>
        logger.error("Could not remove orphan teams from mongo.", e)
        throw e
    }
}
