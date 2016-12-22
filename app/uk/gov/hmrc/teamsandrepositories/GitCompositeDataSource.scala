package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.collection.immutable.Seq
import scala.concurrent.Future


////!@ test this
//@Singleton
//class GithubCompositeDataSourceFactory @Inject()(val githubConfig: GithubConfig,
//                                                 val persister: TeamsAndReposPersister,
//                                                 val mongoConnector: MongoConnector,
//                                                 val githubApiClientDecorator: GithubApiClientDecorator) {
//
//  def buildDataSource: CompositeRepositoryDataSource = {
//    val gitApiEnterpriseClient =
//      githubApiClientDecorator.githubApiClient(githubConfig.githubApiEnterpriseConfig.apiUrl, githubConfig.githubApiEnterpriseConfig.key)
//
//    val enterpriseTeamsRepositoryDataSource: GithubV3RepositoryDataSource =
//      new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, persister, isInternal = true)
//
//    val gitOpenClient =
//      githubApiClientDecorator.githubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
//
//    val openTeamsRepositoryDataSource: GithubV3RepositoryDataSource =
//      new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, persister, isInternal = false)
//
//
//    new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource))
//  }
//}

@Singleton
class GitCompositeDataSource @Inject()(val githubConfig: GithubConfig,
                                       val persister: TeamsAndReposPersister,
                                       val mongoConnector: MongoConnector,
                                       val githubApiClientDecorator: GithubApiClientDecorator) {


  import BlockingIOExecutionContext._

  val gitApiEnterpriseClient: GithubApiClient =
    githubApiClientDecorator.githubApiClient(githubConfig.githubApiEnterpriseConfig.apiUrl, githubConfig.githubApiEnterpriseConfig.key)

  val enterpriseTeamsRepositoryDataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, persister, isInternal = true)

  val gitOpenClient: GithubApiClient =
    githubApiClientDecorator.githubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)

  val openTeamsRepositoryDataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, persister, isInternal = false)

  val dataSources = List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)


  def traverseDataSources: Future[Seq[PersistedTeamAndRepositories]] =
    Future.sequence(dataSources.map(_.persistTeamsAndReposMapping())).map { results =>
      val flattened = results.flatten
      Logger.info(s"Combining ${flattened.length} results from ${dataSources.length} sources")
      flattened.groupBy(_.teamName).map { case (name, teams) =>
        PersistedTeamAndRepositories(name, teams.flatMap(t => t.repositories).sortBy(_.name))
      }.toList

    }


  def removeDeletedTeams: Future[Set[String]] = {

    val collectedTeamNamesTuple: TeamNamesTuple = buildTeamNamesTuple()

    //!@ refactor and match on TeamNameTuple
    val orphanTeamsInMongo: Future[Set[String]] = (collectedTeamNamesTuple.ghNames, collectedTeamNamesTuple.mongoNames) match {
      case (Some(ghTeamNames), Some(mongoTeamNames)) =>
        val orphanTeams: Future[Set[String]] = for {
          ghts <- ghTeamNames
          mongots <- mongoTeamNames
        } yield mongots.filterNot(ghts)
        orphanTeams
      case _ => throw new RuntimeException("no chance we get here!")
    }

    orphanTeamsInMongo.flatMap((teamNames: Set[String]) => persister.deleteTeams(teamNames))
  }


  def buildTeamNamesTuple(): TeamNamesTuple = {
    val teamNamesTuples: Seq[TeamNamesTuple] = dataSources.map(_.getTeamsNamesFromBothSources)

    teamNamesTuples.foldLeft(TeamNamesTuple()) { case (acc: TeamNamesTuple, teamNamesTuple: TeamNamesTuple) =>

      //!@ extract?
      val totalMongoNames: Option[Future[Set[String]]] = (acc.mongoNames, teamNamesTuple.mongoNames) match {
        case (None, Some(mongoTeamNames)) =>
          Some(mongoTeamNames)
        case (Some(accMongoNames), Some(mongoTeamNames)) =>
          Some(Future.sequence(Set(accMongoNames, mongoTeamNames)).map(_.flatten))
        case _ => throw new RuntimeException("unable to collect totalMongoNames")

      }

      //!@ extract?
      val totalGhNames = (acc.ghNames, teamNamesTuple.ghNames) match {
        case (None, Some(ghTeamNames)) =>
          Some(ghTeamNames)
        case (Some(accGhNames), Some(mongoGhNames)) =>
          Some(Future.sequence(Set(accGhNames, mongoGhNames)).map(_.flatten))
        case _ => throw new RuntimeException("unable to collect totalGhNames")
      }

      TeamNamesTuple(totalGhNames, totalMongoNames)

    }
  }
}

@Singleton
case class GithubApiClientDecorator @Inject()() {
  def githubApiClient(apiUrl: String, apiToken: String) = GithubApiClient(apiUrl, apiToken)
}
