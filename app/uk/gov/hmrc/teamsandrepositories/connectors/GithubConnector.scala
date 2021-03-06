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

package uk.gov.hmrc.teamsandrepositories.connectors

import java.net.URL
import java.time.Instant

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, OFormat, __}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpReadsInstances, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.helpers.RetryStrategy

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


@Singleton
class GithubConnector @Inject()(
  githubConfig : GithubConfig,
  httpClient   : HttpClient,
  metrics      : Metrics,
)(implicit ec: ExecutionContext) {
  import RateLimit._

  private val defaultMetricsRegistry = metrics.defaultRegistry

  private val org = "hmrc"
  private val authHeader = "Authorization" -> s"token ${githubConfig.key}"
  private val acceptsHeader = "Accepts" -> "application/vnd.github.v3+json"

  private implicit val hc = HeaderCarrier()

  def getFileContent(repo: GhRepository, path: String): Future[Option[String]] =
    withRetry {
      httpClient.GET[Option[Either[UpstreamErrorResponse, HttpResponse]]](
        url     = url"${githubConfig.rawUrl}/hmrc/${repo.name}/${repo.defaultBranch}/$path", // works with both escaped and non-escaped path
        headers = Seq(authHeader)
      ).map(_.map(_.fold(throw _, _.body)))
    }

  def getTeams(): Future[List[GhTeam]] =
    withCounter(s"github.open.teams") {
      implicit val tf = GhTeam.format
      requestPaginated[GhTeam](url"${githubConfig.apiUrl}/orgs/$org/teams?per_page=100")
    }

  def getTeamDetail(team: GhTeam): Future[Option[GhTeamDetail]] =
    withRetry {
      implicit val rf = GhTeamDetail.format
      requestOptional[GhTeamDetail](url"${githubConfig.apiUrl}/orgs/$org/teams/${team.githubSlug}")
    }

  def getReposForTeam(team: GhTeam): Future[List[GhRepository]] =
    withRetry {
      withCounter(s"github.open.repos") {
        implicit val rf = GhRepository.format
        requestPaginated[GhRepository](url"${githubConfig.apiUrl}/orgs/$org/teams/${team.githubSlug}/repos?per_page=100")
      }
    }

  def getRepos(): Future[List[GhRepository]] =
    withCounter(s"github.open.allRepos") {
      implicit val rf = GhRepository.format
      requestPaginated[GhRepository](url"${githubConfig.apiUrl}/orgs/$org/repos?per_page=100")
    }

  def hasTags(repo: GhRepository): Future[Boolean] =
    withCounter(s"github.open.tags") {
      implicit val tf = GhTag.format
      httpClient.GET[Option[List[GhTag]]](
        url     = url"${githubConfig.apiUrl}/repos/$org/${repo.name}/tags?per_page=1",
        headers = Seq(authHeader, acceptsHeader)
      ).map(_.isDefined)
       .recoverWith(convertRateLimitErrors)
    }

  def existsContent(repo: GhRepository, path: String): Future[Boolean] =
    withRetry {
      withCounter(s"github.open.containsContent") {
        httpClient.GET[Option[Either[UpstreamErrorResponse, HttpResponse]]](
          url     = url"${githubConfig.apiUrl}/repos/$org/${repo.name}/contents/$path", // works with both escaped and non-escaped path
          headers = Seq(authHeader, acceptsHeader)
        ).map{
          case None    => false
          case Some(e) => e.fold(throw _, _ => true)
        }
        .recoverWith(convertRateLimitErrors)
      }
    }

  def getRateLimitMetrics(token: String): Future[RateLimitMetrics] = {
    implicit val rlmr = RateLimitMetrics.reads
    httpClient.GET[RateLimitMetrics](
      url     = s"${githubConfig.apiUrl}/rate_limit",
      headers = Seq("Authorization" -> s"token $token")
    )
  }

  private def withRetry[T](f: => Future[T]): Future[T] =
    RetryStrategy.exponentialRetry(githubConfig.retryCount, githubConfig.retryInitialDelay)(f)

  private case class LinkParam(
    k: String,
    v: String
  )
  private case class PaginatedResult[A](
    results: List[A],
    nextUrl: Option[String]
  )

  private def requestOptional[A : HttpReads](
    url: URL
  )(implicit
    hc: HeaderCarrier
  ): Future[Option[A]] =
    httpClient.GET[Option[A]](
      url     = url,
      headers = Seq(authHeader, acceptsHeader)
    ).recoverWith(convertRateLimitErrors)

  private def requestPaginated[A](
    url: URL,
    acc: List[A] = List.empty
  )(implicit
    hc: HeaderCarrier,
    r : HttpReads[List[A]]
  ): Future[List[A]] = {
    implicit val read: HttpReads[PaginatedResult[A]] =
      for {
        nextUrl <- HttpReadsInstances.readRaw
                     .map(_.header("link").flatMap(lookupNextUrl))
        res     <- r
      } yield PaginatedResult(res, nextUrl)

    httpClient
      .GET[PaginatedResult[A]](
        url     = url"$url",
        headers = Seq(authHeader, acceptsHeader)
      )
      .flatMap { response =>
        val acc2 = acc ++ response.results
        response.nextUrl.fold(Future.successful(acc2))(nextUrl =>
          requestPaginated(url"$nextUrl", acc2)
        )
      }
      .recoverWith(convertRateLimitErrors)
  }

  private def lookupNextUrl(link: String): Option[String] =
    parseLink(link).collectFirst {
      case (url, params) if params.contains(LinkParam("rel", "next")) => url
    }

  // RFC 5988 link header
  private def parseLink(link: String): Seq[(String, List[LinkParam])] =
    link
      .split(",")
      .map { linkEntry =>
        val urlRef :: params = linkEntry.split(";").toList
        val url = urlRef.trim.stripPrefix("<").stripSuffix(">")
        val linkParams = params.map { param =>
          val k :: v :: Nil = param.split("=").toList
          LinkParam(k.trim, v.trim.stripPrefix("\"").stripSuffix("\""))
        }
        url -> linkParams
      }
      .toSeq

  def withCounter[T](name: String)(f: Future[T]) =
    f.andThen {
      case Success(_) =>
        defaultMetricsRegistry.counter(s"$name.success").inc()
      case Failure(_) =>
        defaultMetricsRegistry.counter(s"$name.failure").inc()
    }
}

case class GhTeam(
  id  : Long,
  name: String
) {
  def githubSlug: String =
    name.replaceAll(" - | |\\.", "-").toLowerCase
}

object GhTeam {
  val format: OFormat[GhTeam] =
  ( (__ \ "id"  ).format[Long]
  ~ (__ \ "name").format[String]
  )(apply, unlift(unapply))
}

case class GhTeamDetail(
  id         : Long,
  name       : String,
  createdDate: Instant
) {
  def githubSlug: String =
    name.replaceAll(" - | |\\.", "-").toLowerCase
}

object GhTeamDetail {
  val format: OFormat[GhTeamDetail] =
  ( (__ \ "id"        ).format[Long]
  ~ (__ \ "name"      ).format[String]
  ~ (__ \ "created_at").format[Instant]
  )(apply, unlift(unapply))
}

case class GhRepository(
  id            : Long,
  name          : String,
  description   : Option[String],
  htmlUrl       : String,
  fork          : Boolean,
  createdDate   : Instant,
  lastActiveDate: Instant,
  isPrivate     : Boolean,
  language      : Option[String],
  isArchived    : Boolean,
  defaultBranch : String
)

object GhRepository {
  val format: OFormat[GhRepository] =
    ( (__ \ "id"            ).format[Long]
    ~ (__ \ "name"          ).format[String]
    ~ (__ \ "description"   ).formatNullable[String]
    ~ (__ \ "html_url"      ).format[String]
    ~ (__ \ "fork"          ).format[Boolean]
    ~ (__ \ "created_at"    ).format[Instant]
    ~ (__ \ "pushed_at"     ).format[Instant]
    ~ (__ \ "private"       ).format[Boolean]
    ~ (__ \ "language"      ).formatNullable[String]
    ~ (__ \ "archived"      ).format[Boolean]
    ~ (__ \ "default_branch").format[String]
    )(apply, unlift(unapply))
}

case class GhTag(name: String)
object GhTag {
  val format: OFormat[GhTag] =
    (__ \ "name").format[String].inmap(apply, unlift(unapply))
}



case class RateLimitMetrics(
  limit    : Int,
  remaining: Int,
  reset    : Int
)

object RateLimitMetrics {
  val reads: Reads[RateLimitMetrics] =
    Reads.at(__ \ "rate")(
      ( (__ \ "limit"    ).read[Int]
      ~ (__ \ "remaining").read[Int]
      ~ (__ \ "reset"    ).read[Int]
      )(RateLimitMetrics.apply _)
    )
}

case class ApiRateLimitExceededException(
  exception: Throwable
) extends RuntimeException(exception)

case class ApiAbuseDetectedException(
  exception: Throwable
) extends RuntimeException(exception)

object RateLimit {
  val logger: Logger = Logger(getClass)

  def convertRateLimitErrors[A]: PartialFunction[Throwable, Future[A]] = {
    case e if e.getMessage.toLowerCase.contains("api rate limit exceeded") =>
      logger.error("=== Api rate limit has been reached ===", e)
      Future.failed(ApiRateLimitExceededException(e))

      case e if e.getMessage.toLowerCase.contains("triggered an abuse detection mechanism") =>
      logger.error("=== Api abuse detected ===", e)
      Future.failed(ApiAbuseDetectedException(e))
  }
}
