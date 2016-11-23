package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import com.google.inject.{AbstractModule, TypeLiteral}
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}


sealed trait DataGetter[T] {
  def runner: T
}

case class FileDataGetter(f: DataLoaderFunction) extends DataGetter[DataLoaderFunction] {
  override def runner: DataLoaderFunction = f
}

case class GithubDataGetter(f: DataLoaderFunction) extends DataGetter[DataLoaderFunction] {
  override def runner: DataLoaderFunction = f
}



class Module(environment: play.api.Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {

    val offlineMode = configuration.getBoolean("github.offline.mode").getOrElse(false)

    bind(new TypeLiteral[DataGetter[DataLoaderFunction]]() {}).toInstance(getDataLoader(offlineMode))

    bind(new TypeLiteral[() => LocalDateTime]() {}).toInstance(LocalDateTime.now)

  }

  def getDataLoader(offlineMode: Boolean): DataGetter[DataLoaderFunction] = {
    val dataLoader: DataGetter[DataLoaderFunction] = if (offlineMode) {
      fileDataLoader
    } else {
      githubDataLoader
    }
    dataLoader
  }




  def fileDataLoader: FileDataGetter = {

    implicit val repositoryFormats = Json.format[Repository]
    implicit val teamRepositoryFormats = Json.format[TeamRepositories]

    val cacheFilename = configuration.getString("cacheFilename").getOrElse(throw new RuntimeException("cacheFilename is not specified for off-line (dev) usage"))

    lazy val loadCacheData: Seq[TeamRepositories] = {
      Try(Json.parse(Source.fromFile(cacheFilename).mkString)
        .as[Seq[TeamRepositories]]) match {
        case Success(repos) => repos
        case Failure(e) =>
          e.printStackTrace()
          throw e
      }
    }

    FileDataGetter(() => (Future.successful(loadCacheData)))
  }


  def githubDataLoader: GithubDataGetter = {
    val githubConfig = new GithubConfig(configuration)

    val url = githubConfig.githubApiEnterpriseConfig.apiUrl

    val gitApiEnterpriseClient = GithubApiClient(url, githubConfig.githubApiEnterpriseConfig.key)

    val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, isInternal = true)

    val gitOpenClient = GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
    val openTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, isInternal = false)

    GithubDataGetter(new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _)
  }


}
