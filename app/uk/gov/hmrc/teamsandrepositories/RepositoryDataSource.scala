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

import java.time.LocalDateTime

import com.google.inject.{Inject, Singleton}
import org.joda.time.Duration
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.githubclient.{GhOrganisation, GhRepository, GhTeam, GithubApiClient}
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}
import uk.gov.hmrc.teamsandrepositories.RepoType._
import uk.gov.hmrc.teamsandrepositories.RetryStrategy._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig


import scala.concurrent.Future
import scala.util.{Failure, Success}


//case class TeamRepositories(teamName: String, repositories: List[GitRepository]) {
//  def repositoriesByType(repoType: RepoType.RepoType) = repositories.filter(_.repoType == repoType)
//}


case class GitRepository(name: String,
                         description: String,
                         url: String,
                         createdDate: Long,
                         lastActiveDate: Long,
                         isInternal: Boolean = false,
                         repoType: RepoType = RepoType.Other)

object GitRepository {
  implicit val gitRepositoryFormats = Json.format[GitRepository]
}


case class TeamNamesTuple(ghNames: Option[Future[Set[String]]] = None, mongoNames: Option[Future[Set[String]]] = None)

//case object TeamNamesTuple {
//  def apply(ghName: Future[Set[String]], mongoNames: Future[Set[String]]): TeamNamesTuple =
//    TeamNamesTuple(Some(ghName), Some(mongoNames))
//
//}

@Singleton
class GithubV3RepositoryDataSource @Inject()(githubConfig: GithubConfig,
                                             gh: GithubApiClient,
                                             persister: TeamsAndReposPersister,
                                             val isInternal: Boolean) {


  import BlockingIOExecutionContext._

  implicit val repositoryFormats = Json.format[GitRepository]

  implicit val teamRepositoryFormats = Json.format[TeamRepositories]

  val retries: Int = 5
  val initialDuration: Double = 50

  //!@ remove once done
  def persistTeamsAndReposMapping(): Future[Seq[TeamRepositories]] = {
    exponentialRetry(retries, initialDuration) {
      gh.getOrganisations.flatMap { (orgs: Seq[GhOrganisation]) =>
        Future.sequence(orgs.map(org => traverseOrganisation(org))).map {
          _.flatten
        }
      }
    }.andThen {
      case Failure(t) => throw t
      case Success(_) => persister.updateTimestamp(LocalDateTime.now())
    }
  }

  def getTeamRepoMapping: Future[Seq[TeamRepositories]] =
    exponentialRetry(retries, initialDuration) {
      gh.getOrganisations.flatMap { orgs =>
        Future.sequence(orgs.map(mapOrganisation)).map {
          _.flatten
        }
      }
    }

  def mapOrganisation(organisation: GhOrganisation): Future[List[TeamRepositories]] =
    exponentialRetry(retries, initialDuration) {
      gh.getTeamsForOrganisation(organisation.login).flatMap { teams =>
        Future.sequence(for {
          team <- teams; if !githubConfig.hiddenTeams.contains(team.name)
        } yield mapTeam(organisation, team))
      }
    }


  def mapTeam(organisation: GhOrganisation, team: GhTeam): Future[TeamRepositories] =
    exponentialRetry(retries, initialDuration) {
      gh.getReposForTeam(team.id).flatMap { repos =>
        Future.sequence(for {
          repo <- repos; if !repo.fork && !githubConfig.hiddenRepositories.contains(repo.name)
        } yield mapRepository(organisation, repo)).map { (repos: List[GitRepository]) =>
          TeamRepositories(team.name, repositories = repos)
        }
      }
    }


  def getTeamsNamesFromMongo: Future[Set[String]] = {

//    val teamNamesFromGh: Future[Set[String]] = gh.getOrganisations.flatMap { orgs: Seq[GhOrganisation] =>
//      val teams: Seq[Future[Seq[String]]] = orgs.map(org => gh.getTeamsForOrganisation(org.login).map((x: Seq[GhTeam]) => x.map(_.name)))
//      Future.sequence(teams).map(_.flatten.toSet.filter(ts => !githubConfig.hiddenTeams.contains(ts)))
//    }

    val teamNamesFromMongo: Future[Set[String]] = {
      persister.getAllTeamAndRepos.map { case (allPersistedTeamAndRepositories, _) =>
        allPersistedTeamAndRepositories.map(_.teamName).toSet
      }
    }

    teamNamesFromMongo

  }


  private def traverseOrganisation(organisation: GhOrganisation): Future[List[TeamRepositories]] = {
    exponentialRetry(retries, initialDuration) {
      for {
        teamsFromGh <- gh.getTeamsForOrganisation(organisation.login)
        persistedTeams: Seq[TeamRepositories] <- persistGhTeams(organisation, teamsFromGh)
      } yield persistedTeams.toList
    }
  }

  private def persistGhTeams(organisation: GhOrganisation, teamsFromGh: List[GhTeam]) = {
    Future.sequence(
      for {
        team <- teamsFromGh if !githubConfig.hiddenTeams.contains(team.name)
      } yield persistTeam(organisation, team))
  }

  private def persistTeam(organisation: GhOrganisation, team: GhTeam): Future[TeamRepositories] =
    exponentialRetry(retries, initialDuration) {
      val reposForTeam = gh.getReposForTeam(team.id)
      reposForTeam.flatMap { repos =>
        val gitRepositoriesForTeam = Future.sequence(for {
          repo <- repos if !repo.fork && !githubConfig.hiddenRepositories.contains(repo.name)
        } yield mapRepository(organisation, repo))
        gitRepositoriesForTeam.flatMap(rs => persister.update(TeamRepositories(team.name, rs)))
      }
    }


  private def mapRepository(organisation: GhOrganisation, repo: GhRepository): Future[GitRepository] = {

    isDeployable(repo, organisation) flatMap { deployable =>

      val repository: GitRepository = GitRepository(repo.name, repo.description, repo.htmlUrl, createdDate = repo.createdDate, lastActiveDate = repo.lastActiveDate, isInternal = this.isInternal)

      if (deployable) {
        Future.successful(repository.copy(repoType = RepoType.Deployable))
      } else {
        isLibrary(repo, organisation).map { tags =>
          if (tags) repository.copy(repoType = RepoType.Library)
          else repository
        }
      }
    }
  }

  private def isLibrary(repo: GhRepository, organisation: GhOrganisation) = {
    import uk.gov.hmrc.teamsandrepositories.FutureExtras._

    def hasSrcMainScala =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "src/main/scala"))

    def hasSrcMainJava =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "src/main/java"))

    def containsTags =
      hasTags(organisation, repo)

    (hasSrcMainScala || hasSrcMainJava) && containsTags
  }

  private def isDeployable(repo: GhRepository, organisation: GhOrganisation) = {
    import uk.gov.hmrc.teamsandrepositories.FutureExtras._

    def isPlayServiceF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "conf/application.conf"))

    def hasProcFileF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "Procfile"))

    def isJavaServiceF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "deploy.properties"))

    isPlayServiceF || isJavaServiceF || hasProcFileF
  }

  private def hasTags(organisation: GhOrganisation, repository: GhRepository) =
    gh.getTags(organisation.login, repository.name).map(_.nonEmpty)

  private def hasPath(organisation: GhOrganisation, repo: GhRepository, path: String) =
    gh.repoContainsContent(path, repo.name, organisation.login)

}

@Singleton
class MongoLock @Inject()(mongoConnector: MongoConnector) extends LockKeeper {
  override def repo: LockRepository = LockMongoRepository(mongoConnector.db)

  override def lockId: String = "teams-and-repositories-sync-job"

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(20)
}
