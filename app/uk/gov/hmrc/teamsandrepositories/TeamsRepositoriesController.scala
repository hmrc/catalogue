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

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.concurrent.Executors

import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Play}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc.{Results, _}
import play.libs.Akka
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.teamsandrepositories.config.{GithubConfig, UrlTemplatesProvider, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


case class Environment(name: String, services: Seq[Link])

case class Link(name: String, displayName: String, url: String)

case class RepositoryDetails(name: String,
                             description: String,
                             createdAt: Long,
                             lastActive: Long,
                             repoType: RepoType.RepoType,
                             teamNames: Seq[String],
                             githubUrls: Seq[Link],
                             ci: Seq[Link] = Seq.empty,
                             environments: Seq[Environment] = Seq.empty)

case class RepositoryDisplayDetails(name: String, createdAt: Long, lastUpdatedAt: Long)

object RepositoryDisplayDetails {
  implicit val repoDetailsFormat = Json.format[RepositoryDisplayDetails]
}

case class Team(name: String,
                firstActiveDate: Option[Long] = None,
                lastActiveDate: Option[Long] = None,
                repos: Map[RepoType.Value, Seq[String]])

object Team {

  implicit val mapReads: Reads[Map[RepoType.RepoType, Seq[String]]] = new Reads[Map[RepoType.RepoType, Seq[String]]] {
    def reads(jv: JsValue): JsResult[Map[RepoType.RepoType, Seq[String]]] =
      JsSuccess(jv.as[Map[String, Seq[String]]].map { case (k, v) =>
        RepoType.withName(k) -> v.asInstanceOf[Seq[String]]
      })
  }

  implicit val mapWrites: Writes[Map[RepoType.RepoType, Seq[String]]] = new Writes[Map[RepoType.RepoType, Seq[String]]] {
    def writes(map: Map[RepoType.RepoType, Seq[String]]): JsValue =
      Json.obj(map.map { case (s, o) =>
        val ret: (String, JsValueWrapper) = s.toString -> JsArray(o.map(JsString))

        ret
      }.toSeq: _*)
  }

  implicit val mapFormat: Format[Map[RepoType.RepoType, Seq[String]]] = Format(mapReads, mapWrites)


  implicit val format = Json.format[Team]
}

object BlockingIOExecutionContext {
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(32))
}


//@Singleton
//class DataLoader @Inject()(configuration: Configuration, githubConfig: GithubConfig) {
//
//  private val githubIntegrationEnabled = configuration.getBoolean("github.integration.enabled").getOrElse(true)
//
//  private val gitApiEnterpriseClient = GithubApiClient(githubConfig.githubApiEnterpriseConfig.apiUrl, githubConfig.githubApiEnterpriseConfig.key)
//
//  private val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
//    new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, isInternal = true)
//
//  private val gitOpenClient = GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
//  private val openTeamsRepositoryDataSource: RepositoryDataSource =
//    new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, isInternal = false)
//
//  private def load: () => Future[Seq[TeamRepositories]] = new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _
//
//  private val cachedDataSource =
//    if (githubIntegrationEnabled) {
//      new MemoryCachedRepositoryDataSource[Seq[TeamRepositories]](
//        CacheConfig,
//        new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _,
//        LocalDateTime.now
//      )
//    } else {
//      val cacheFilename = configuration.getString("cacheFilename").getOrElse(throw new RuntimeException("cacheFilename is not specified for off-line (dev) usage"))
//      new FileCachedRepositoryDataSource(cacheFilename)
//    }
//
//
//  def cachedData: Future[CachedResult[Seq[TeamRepositories]]] = cachedDataSource.getCachedTeamRepoMapping
//
//  def reload(): Unit = {
//    cachedDataSource.reload()
//  }
//
//
//}


class TeamsRepositoriesController @Inject()(dataLoader: CachedRepositoryDataSource[Seq[TeamRepositories]], urlTemplatesProvider: UrlTemplatesProvider, configuration: Configuration) extends BaseController {

  import TeamRepositoryWrapper._

  val CacheTimestampHeaderName = "X-Cache-Timestamp"

  import scala.collection.JavaConverters._

  val repositoriesToIgnore: List[String] = configuration.getStringList("shared.repositories").fold(List.empty[String])(_.asScala.toList)


  implicit val environmentFormats = Json.format[Link]
  implicit val linkFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]

  private def format(dateTime: LocalDateTime): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(dateTime, ZoneId.of("GMT")))
  }

  def repositoryDetails(name: String) = Action.async { implicit request =>
    dataLoader.getCachedTeamRepoMapping.map { cachedTeams =>
      (cachedTeams.data.findRepositoryDetails(name, urlTemplatesProvider.ciUrlTemplates) match {
        case None => NotFound
        case Some(x: RepositoryDetails) => Results.Ok(Json.toJson(x))
      }).withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }

  def services() = Action.async { implicit request =>
    dataLoader.getCachedTeamRepoMapping.map { (cachedTeams: CachedResult[Seq[TeamRepositories]]) =>
      Ok(determineServicesResponse(request, cachedTeams.data))
        .withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }


  import RepositoryDisplayDetails._

  private def determineServicesResponse(request: Request[AnyContent], data: Seq[TeamRepositories]) =
    if (request.getQueryString("details").nonEmpty)
      Json.toJson(data.asRepositoryDetailsList(RepoType.Deployable, urlTemplatesProvider.ciUrlTemplates))
    else if (request.getQueryString("teamDetails").nonEmpty)
      Json.toJson(data.asRepositoryTeamNameList())
    else Json.toJson(data.asServiceRepoDetailsList)

  def libraries() = Action.async { implicit request =>
    dataLoader.getCachedTeamRepoMapping.map { cachedTeams =>
      Ok(determineLibrariesResponse(request, cachedTeams.data))
        .withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }

  private def determineLibrariesResponse(request: Request[AnyContent], data: Seq[TeamRepositories]) = {
    if (request.getQueryString("details").nonEmpty)
      Json.toJson(data.asRepositoryDetailsList(RepoType.Library, urlTemplatesProvider.ciUrlTemplates))
    else
      Json.toJson(data.asLibraryRepoDetailsList)
  }

  def teams() = Action.async { implicit request =>
    dataLoader.getCachedTeamRepoMapping.map { cachedTeams =>
      Results.Ok(Json.toJson(cachedTeams.data.asTeamList(repositoriesToIgnore)))
        .withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }

  def repositoriesByTeam(teamName: String) = Action.async { implicit request =>
    dataLoader.getCachedTeamRepoMapping.map { cachedTeams =>
      (cachedTeams.data.asTeamRepositoryNameList(teamName) match {
        case None => NotFound
        case Some(x) => Results.Ok(Json.toJson(x.map { case (t, v) => (t.toString, v) }))
      }).withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }


  def repositoriesWithDetailsByTeam(teamName: String) = Action.async { implicit request =>
    dataLoader.getCachedTeamRepoMapping.map { cachedTeams =>
      (cachedTeams.data.asTeamRepositoryDetailsList(teamName, repositoriesToIgnore) match {
        case None => NotFound
        case Some(x) => Results.Ok(Json.toJson(x))
      }).withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }

  def reloadCache() = Action { implicit request =>
    dataLoader.reload()
    Ok("Cache reload triggered successfully")
  }

  def save = Action.async { implicit request =>
    val file: Option[String] = request.getQueryString("file")

    file match {
      case Some(filename) =>
        dataLoader.getCachedTeamRepoMapping.map { cachedTeams =>
          import java.io._
          implicit val repositoryFormats = Json.format[Repository]
          implicit val teamRepositoryFormats = Json.format[TeamRepositories]
          val pw = new PrintWriter(new File(filename))
          pw.write(Json.stringify(Json.toJson(cachedTeams.data)))
          pw.close()

          Ok(s"Saved $file").withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
        }
      case None =>
        Future(NotAcceptable(s"no file specified").withHeaders(CacheTimestampHeaderName -> format(LocalDateTime.now())))
    }

  }

}
