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

import java.net.URLDecoder
import uk.gov.hmrc.teamsandrepositories.RepoType
import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}

import scala.collection.immutable.::


object TeamRepositoryWrapper {

  implicit class TeamRepositoryWrapper(teamRepos: Seq[TeamRepositories]) {

    def asTeamNameList = teamRepos.map(_.teamName)

    def asServiceNameList = asNameListOfGivenRepoType(RepoType.Deployable)

    def asLibraryNameList: Seq[String] = asNameListOfGivenRepoType(RepoType.Library)

    def findRepositoryDetails(repoName: String, ciUrlTemplates: UrlTemplates): Option[RepositoryDetails] = {
      val decodedServiceName = URLDecoder.decode(repoName, "UTF-8")

      teamRepos.foldLeft((Set.empty[String], Set.empty[Repository])) { case ((ts, repos), tr) =>
        if (tr.repositories.exists(_.name == decodedServiceName))
          (ts + tr.teamName, repos ++ tr.repositories.filter(_.name == decodedServiceName))
        else (ts, repos)
      } match {
        case (teams, repos) if repos.nonEmpty =>
          repoGroupToRepositoryDetails(primaryRepoType(repos.toSeq), repos.toSeq, teams.toSeq.sorted, ciUrlTemplates)
        case _ => None
      }
    }

    def asRepositoryDetailsList(repoType: RepoType, ciUrlTemplates: UrlTemplates): Seq[RepositoryDetails] = {
      repositoryTeams(teamRepos)
        .groupBy(_.repositories)
        .flatMap { case (repositories, t) => repoGroupToRepositoryDetails(repoType, repositories, t.map(_.teamName), ciUrlTemplates) }
        .toSeq
        .sortBy(_.name.toUpperCase)
    }

    private def primaryRepoType(repositories: Seq[Repository]): RepoType = {
      if (repositories.exists(_.repoType == RepoType.Deployable)) RepoType.Deployable
      else if (repositories.exists(_.repoType == RepoType.Library)) RepoType.Library
      else RepoType.Other
    }


    def asTeamRepositoryNameList(teamName: String): Option[Map[RepoType.RepoType, List[String]]] = {
      val decodedTeamName = URLDecoder.decode(teamName, "UTF-8")
      teamRepos.find(_.teamName == decodedTeamName).map { t =>

        RepoType.values.foldLeft(Map.empty[RepoType.Value, List[String]]) { case (m, rtype) =>
          m + (rtype -> extractRepositoryGroupForType(rtype, t.repositories).map(_.name).distinct.sortBy(_.toUpperCase))
        }

      }
    }
//
//    def asRepositoryTeamNameList(repoName: String): Option[Map[String, Seq[String]]] = {
//      ???
////      val decodedRepoName = URLDecoder.decode(repoName, "UTF-8")
////      teamRepos.filter(_.repositories.exists(r => r.name == decodedRepoName)) match {
////        case x if x.isEmpty => None
////        case x => Some(x.map { repoName -> _.teamName } toMap) }
//    }

    private case class RepositoryToTeam(repositoryName: String, teamName: String)

    def asRepositoryTeamNameList(): Map[String, Seq[String]] = {
      val mappings = for {
        tr <- teamRepos
        r <- tr.repositories
      } yield RepositoryToTeam(r.name, tr.teamName)

      mappings.groupBy(_.repositoryName)
        .map { m => m._1 -> m._2.map(_.teamName).distinct }
    }

    private case class RepositoriesToTeam(repositories: Seq[Repository], teamName: String)

    private def asNameListOfGivenRepoType(repoType: RepoType.Value): Seq[String] = {
      val repoNames = for {
        d <- teamRepos
        r <- extractRepositoryGroupForType(repoType, d.repositories)
      } yield r.name

      repoNames
        .distinct
        .sortBy(_.toUpperCase)
    }


    private def repositoryTeams(data: Seq[TeamRepositories]): Seq[RepositoriesToTeam] =
      for {
        team <- data
        repositories <- team.repositories.groupBy(_.name).values
      } yield RepositoriesToTeam(repositories, team.teamName)
  }

  def repoGroupToRepositoryDetails(repoType: RepoType, repositories: Seq[Repository], teamNames: Seq[String], urlTemplates: UrlTemplates): Option[RepositoryDetails] = {

    val primaryRepository = extractRepositoryGroupForType(repoType, repositories).find(_.repoType == repoType)

    buildRepositoryDetails(primaryRepository, repositories, teamNames, urlTemplates)

  }

  def buildRepositoryDetails(primaryRepository: Option[Repository], allRepositories: Seq[Repository], teamNames: Seq[String], urlTemplates: UrlTemplates): Option[RepositoryDetails] = {

    primaryRepository.map { repo =>

      val repoDetails = RepositoryDetails(
        repo.name,
        repo.repoType,
        teamNames,
        allRepositories.map { repo =>
          Link(
            githubName(repo.isInternal),
            githubDisplayName(repo.isInternal),
            repo.url)
        })

      val repositoryForCiUrls: Repository = allRepositories.find(!_.isInternal).fold(repo)(identity)

      if (hasEnvironment(repo))
        repoDetails.copy(ci = buildCiUrls(repositoryForCiUrls, urlTemplates), environments = buildEnvironmentUrls(repo, urlTemplates))
      else if (hasBuild(repo))
        repoDetails.copy(ci = buildCiUrls(repositoryForCiUrls, urlTemplates))
      else repoDetails
    }
  }

  private def githubName(isInternal: Boolean) = if (isInternal) "github-enterprise" else "github-com"

  private def githubDisplayName(isInternal: Boolean) = if (isInternal) "Github Enterprise" else "GitHub.com"

  private def hasEnvironment(repo: Repository): Boolean = repo.repoType == RepoType.Deployable

  private def hasBuild(repo: Repository): Boolean = repo.repoType == RepoType.Library

  private def buildEnvironmentUrls(repository: Repository, urlTemplates: UrlTemplates): Seq[Environment] = {
    urlTemplates.environments.map { case (name, tps) =>
      val links = tps.map { tp => Link(tp.name, tp.displayName, tp.url(repository.name)) }
      Environment(name, links)
    }.toSeq
  }

  private def buildCiUrls(repository: Repository, urlTemplates: UrlTemplates): List[Link] =
    repository.isInternal match {
      case true => buildUrls(repository, urlTemplates.ciClosed)
      case false => buildUrls(repository, urlTemplates.ciOpen)
    }

  private def buildUrls(repo: Repository, templates: Seq[UrlTemplate]) = templates.map(t => Link(t.name, t.displayName, t.url(repo.name))).toList


  def extractRepositoryGroupForType(repoType: RepoType.RepoType, repositories: Seq[Repository]): List[Repository] = {
    repositories
      .groupBy(_.name)
      .filter {
        case (name, repos) if repoType == RepoType.Deployable =>
          repos.exists(x => x.repoType == RepoType.Deployable)
        case (name, repos) if repoType == RepoType.Library =>
          !repos.exists(x => x.repoType == RepoType.Deployable) && repos.exists(x => x.repoType == RepoType.Library)
        case (name, repos) =>
          !repos.exists(x => x.repoType == RepoType.Deployable) && !repos.exists(x => x.repoType == RepoType.Library) && repos.exists(x => x.repoType == repoType)
      }
      .flatMap(_._2).filter(x => !x.name.contains("prototype")).toList
  }

}
