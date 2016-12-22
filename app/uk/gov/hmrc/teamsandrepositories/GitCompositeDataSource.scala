package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import com.google.inject.{Inject, Singleton}
import play.Logger
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig


import scala.concurrent.Future
import scala.util.{Failure, Success}


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


//  //!@ remove this once done
//  def traverseDataSources: Future[Seq[TeamRepositories]] =
//    Future.sequence(dataSources.map(_.persistTeamsAndReposMapping())).map { results =>
//      val flattened = results.flatten
//      Logger.info(s"Combining ${flattened.length} results from ${dataSources.length} sources")
//      flattened.groupBy(_.teamName).map { case (name, teams) =>
//        TeamRepositories(name, teams.flatMap(t => t.repositories).sortBy(_.name))
//      }.toList
//
//    }


  def persistTeamRepoMapping: Future[Seq[TeamRepositories]] = {

    Future.sequence(dataSources.map(_.getTeamRepoMapping)).map { results =>
      val flattened: List[TeamRepositories] = results.flatten

      Logger.info(s"Combining ${flattened.length} results from ${dataSources.length} sources")
      Future.sequence(flattened.groupBy(_.teamName).map { case (name, teams) =>
        TeamRepositories(name, teams.flatMap(t => t.repositories).sortBy(_.name))
      }.toList.map(tr => persister.update(tr)))
    }.flatMap(identity).andThen {
      case Failure(t) => throw t
      case Success(_) => persister.updateTimestamp(LocalDateTime.now()) //!@ test this
    }
  }

  def removeOrphanTeamsFromMongo(teamRepositoriesFromGh: Seq[TeamRepositories]) = {

    val teamNamesFromMongo: Future[Set[String]] = {
      persister.getAllTeamAndRepos.map { case (allPersistedTeamAndRepositories, _) =>
        allPersistedTeamAndRepositories.map(_.teamName).toSet
      }
    }

    val teamNamesFromGh = teamRepositoriesFromGh.map(_.teamName)

    val orphanTeams: Future[Set[String]] = for {
      mongots <- teamNamesFromMongo
    } yield mongots.filterNot(teamNamesFromGh.toSet)

    orphanTeams.flatMap((teamNames: Set[String]) => persister.deleteTeams(teamNames))
  }


//  def buildTeamNamesTuple(): TeamNamesTuple = {
//
//    val teamNamesFromMongo: Future[Set[String]] = {
//      persister.getAllTeamAndRepos.map { case (allPersistedTeamAndRepositories, _) =>
//        allPersistedTeamAndRepositories.map(_.teamName).toSet
//      }
//    }
//
//
//    //    val teamNamesTuples: List[Future[Set[String]]] = dataSources.map(_.getTeamsNamesFromMongo)
//
//    teamNamesFromMongo.foldLeft(TeamNamesTuple()) { case (acc: TeamNamesTuple, teamNamesTuple: TeamNamesTuple) =>
//
//      //!@ extract?
//      val totalMongoNames: Option[Future[Set[String]]] = (acc.mongoNames, teamNamesTuple.mongoNames) match {
//        case (None, Some(mongoTeamNames)) =>
//          Some(mongoTeamNames)
//        case (Some(accMongoNames), Some(mongoTeamNames)) =>
//          Some(Future.sequence(Set(accMongoNames, mongoTeamNames)).map(_.flatten))
//        case _ => throw new RuntimeException("unable to collect totalMongoNames")
//
//      }
//
//      //      //!@ extract?
//      //      val totalGhNames = (acc.ghNames, teamNamesTuple.ghNames) match {
//      //        case (None, Some(ghTeamNames)) =>
//      //          Some(ghTeamNames)
//      //        case (Some(accGhNames), Some(mongoGhNames)) =>
//      //          Some(Future.sequence(Set(accGhNames, mongoGhNames)).map(_.flatten))
//      //        case _ => throw new RuntimeException("unable to collect totalGhNames")
//      //      }
//
//      totalMongoNames
//
//    }
//  }
}

@Singleton
case class GithubApiClientDecorator @Inject()() {
  def githubApiClient(apiUrl: String, apiToken: String) = GithubApiClient(apiUrl, apiToken)
}
