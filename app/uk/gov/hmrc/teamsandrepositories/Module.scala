package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import com.google.inject.{AbstractModule, TypeLiteral}
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

class Module(environment: play.api.Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {

    bind(new TypeLiteral[DataSynchroniser]() {}).toInstance(githubDataLoader)

    bind(new TypeLiteral[() => LocalDateTime]() {}).toInstance(LocalDateTime.now)

    bind(classOf[ReloadScheduler]).asEagerSingleton()


  }

  //!@
//  def getDataLoader(offlineMode: Boolean): DataGetterPersister[PersistedTeamAndRepositories] = {
//    val dataLoader: DataGetterPersister[PersistedTeamAndRepositories] = if (offlineMode) {
//      fileDataLoader
//    } else {
//      githubDataLoader
//    }
//    dataLoader
//  }



  //!@

  //  def fileDataLoader: FileDataGetterPersister = {
//
//    implicit val repositoryFormats = Json.format[GitRepository]
//    implicit val teamRepositoryFormats = Json.format[TeamRepositories]
//
//    val cacheFilename = configuration.getString("cacheFilename").getOrElse(throw new RuntimeException("cacheFilename is not specified for off-line (dev) usage"))
//
//    lazy val loadCacheData: Seq[TeamRepositories] = {
//      Try(Json.parse(Source.fromFile(cacheFilename).mkString)
//        .as[Seq[TeamRepositories]]) match {
//        case Success(repos) => repos
//        case Failure(e) =>
//          e.printStackTrace()
//          throw e
//      }
//    }
//
//    //!@FileDataGetterPersister(() => Future.successful(loadCacheData))
//    ???
//  }


  def githubDataLoader: GithubDataSynchroniser = {
    val githubConfig = new GithubConfig(configuration)

    val url = githubConfig.githubApiEnterpriseConfig.apiUrl

    val gitApiEnterpriseClient = GithubApiClient(url, githubConfig.githubApiEnterpriseConfig.key)

    val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, isInternal = true)

    val gitOpenClient = GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
    val openTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, isInternal = false)

    //!@
//    val runner:() => Future..
    val runner: (TeamsAndReposPersister) => Future[Seq[PersistedTeamAndRepositories]]
    = new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).persistTeamsAndReposMapping _
//    val runner: (TeamsAndReposPersister) => Future[Seq[PersistedTeamAndRepositories]] = new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource)).persistTeamsAndReposMapping _
    GithubDataSynchroniser(runner)
  }

  //!@

  //  def githubDataLoader: GithubDataGetter = {
//    val githubConfig = new GithubConfig(configuration)
//
//    val url = githubConfig.githubApiEnterpriseConfig.apiUrl
//
//    val gitApiEnterpriseClient = GithubApiClient(url, githubConfig.githubApiEnterpriseConfig.key)
//
//    val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
//      new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, isInternal = true)
//
//    val gitOpenClient = GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
//    val openTeamsRepositoryDataSource: RepositoryDataSource =
//      new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, isInternal = false)
//
//    GithubDataGetter(new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _)
//  }


}
