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

package uk.gov.hmrc.teamsandservices

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.concurrent.Executors

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, Result}
import play.libs.Akka
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.teamsandservices.DataSourceToApiContractMappings._
import uk.gov.hmrc.teamsandservices.config._

import scala.concurrent.{ExecutionContext, Future}


case class Link(name: String, url: String)
case class TeamServices(teamName: String, Services: List[Service])
case class Service(name: String, teamNames: Seq[String], githubUrls: Seq[Link], ci: List[Link])


object TeamsServicesController extends TeamsServicesController
with UrlTemplatesProvider {

  private val githubClientEc = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(32))

  private val gitApiEnterpriseClient = GithubApiClient(GithubConfig.githubApiEnterpriseConfig.apiUrl, GithubConfig.githubApiEnterpriseConfig.key)

  private val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
    new GithubV3RepositoryDataSource(gitApiEnterpriseClient, isInternal = true, githubClientEc) with GithubConfigProvider

  private val gitOpenClient = GithubApiClient(GithubConfig.githubApiOpenConfig.apiUrl, GithubConfig.githubApiOpenConfig.key)
  private val openTeamsRepositoryDataSource: RepositoryDataSource =
    new GithubV3RepositoryDataSource(gitOpenClient, isInternal = false, githubClientEc) with GithubConfigProvider

  private def dataLoader: () => Future[Seq[TeamRepositories]] = new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource), githubClientEc).getTeamRepoMapping _

  protected val dataSource: CachingRepositoryDataSource[Seq[TeamRepositories]] = new CachingRepositoryDataSource[Seq[TeamRepositories]](
    Akka.system(), CacheConfig,
    dataLoader,
    LocalDateTime.now
  )
}

trait TeamsServicesController extends BaseController {

  import Results._

  protected def ciUrlTemplates: UrlTemplates

  protected def dataSource: CachingRepositoryDataSource[Seq[TeamRepositories]]

  implicit val linkFormats = Json.format[Link]
  implicit val serviceFormats = Json.format[Service]
  implicit val teamFormats = Json.format[TeamServices]

  def services() = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map { teams =>
      OkWithCachedTimestamp(teams.asServicesList(ciUrlTemplates))
    }
  }

  def teams() = Action.async { implicit request =>
    Logger.info("fetching teams info")
    dataSource.getCachedTeamRepoMapping.map { teams =>
      OkWithCachedTimestamp(teams.asTeamsList)
    }
  }

  def teamServices(teamName: String) = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map { teams =>
      val cached = teams.asTeamServices(teamName, ciUrlTemplates)
      cached.data match {
        case None => NotFound
        case Some(x) => OkWithCachedTimestamp(x)
      }
    }
  }

  def reloadCache() = Action { implicit request =>
    dataSource.reload()
    Ok("Cache reload triggered successfully")
  }
}
