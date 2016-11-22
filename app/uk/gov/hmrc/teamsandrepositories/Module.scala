package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Inject, TypeLiteral}
import play.api.Configuration
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.json.Json
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.config.{CacheConfig, GithubConfig}

import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

class Module(environment: play.api.Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {

    val offlineMode = configuration.getBoolean("github.offline.mode").getOrElse(false)

    bind(new TypeLiteral[() => Future[Seq[TeamRepositories]]]() {}).toInstance(getDataLoader(offlineMode))

    bind(new TypeLiteral[() => LocalDateTime]() {}).toInstance(LocalDateTime.now)

    //
    //      val mem = new MemoryCachedRepositoryDataSource[Seq[TeamRepositories]](
    ////        new CacheConfig(configuration),
    //        new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _,
    //        LocalDateTime.now
    //      )

    //      bind(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]{}).toInstance(mem)
    //    } else {
    //      val cacheFilename = configuration.getString("cacheFilename").getOrElse(throw new RuntimeException("cacheFilename is not specified for off-line (dev) usage"))
    //      val file = new FileCachedRepositoryDataSource(cacheFilename)
    //      bind(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]{})
    //        .toInstance(file)
    //    }
  }

  def getDataLoader(offlineMode: Boolean): () => Future[Seq[TeamRepositories]] = {
    val dataLoader: () => Future[Seq[TeamRepositories]] = if (offlineMode) {
      fileDataLoader
    } else {
      githubDataLoader
    }
    dataLoader
  }

  //  override def configure(): Unit = {
  //    val useMemoryDataCache = configuration.getBoolean("github.integration.enabled").getOrElse(false)
  //    if (useMemoryDataCache) {
  //      val githubConfig = new GithubConfig(configuration)
  //
  //      val url = githubConfig.githubApiEnterpriseConfig.apiUrl
  //
  //      val gitApiEnterpriseClient = GithubApiClient(url, githubConfig.githubApiEnterpriseConfig.key)
  //
  //      val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
  //        new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, isInternal = true)
  //
  //      val gitOpenClient = GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
  //      val openTeamsRepositoryDataSource: RepositoryDataSource =
  //        new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, isInternal = false)
  //
  //      val mem = new MemoryCachedRepositoryDataSource[Seq[TeamRepositories]](
  ////        new CacheConfig(configuration),
  //        new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _,
  //        LocalDateTime.now
  //      )
  //
  //      bind(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]{}).toInstance(mem)
  //    } else {
  //      val cacheFilename = configuration.getString("cacheFilename").getOrElse(throw new RuntimeException("cacheFilename is not specified for off-line (dev) usage"))
  //      val file = new FileCachedRepositoryDataSource(cacheFilename)
  //      bind(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]{})
  //        .toInstance(file)
  //    }
  //  }
  def fileDataLoader: () => Future[Seq[TeamRepositories]] with FDL = {

    implicit val repositoryFormats = Json.format[Repository]
    implicit val teamRepositoryFormats = Json.format[TeamRepositories]

    val cacheFilename = configuration.getString("cacheFilename").getOrElse(throw new RuntimeException("cacheFilename is not specified for off-line (dev) usage"))
    val file = new FileCachedRepositoryDataSource(cacheFilename)

    lazy val loadCacheData: Seq[TeamRepositories] = {
      Try(Json.parse(Source.fromFile(cacheFilename).mkString)
        .as[Seq[TeamRepositories]]) match {
        case Success(repos) => repos
        case Failure(e) =>
          e.printStackTrace()
          throw e
      }
    }

    () => (Future.successful(loadCacheData))
  }

  trait FDL
  trait GDL

  def githubDataLoader: () => Future[Seq[TeamRepositories]] with GDL = {
    val githubConfig = new GithubConfig(configuration)

    val url = githubConfig.githubApiEnterpriseConfig.apiUrl

    val gitApiEnterpriseClient = GithubApiClient(url, githubConfig.githubApiEnterpriseConfig.key)

    val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, isInternal = true)

    val gitOpenClient = GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
    val openTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, isInternal = false)

    new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _
  }


}
