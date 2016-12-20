package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig


//!@ test this
@Singleton
case class SynchroniserFactory @Inject()(githubConfig: GithubConfig,
                                         persister: TeamsAndReposPersister,
                                         mongoConnector: MongoConnector,
                                         githubApiClientDecorator: GithubApiClientDecorator) {

  def getSynchroniser: GithubDataSynchroniser = {
    val gitApiEnterpriseClient =
      githubApiClientDecorator.githubApiClient(githubConfig.githubApiEnterpriseConfig.apiUrl, githubConfig.githubApiEnterpriseConfig.key)

    val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, persister, isInternal = true)

    val gitOpenClient =
      githubApiClientDecorator.githubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)

    val openTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, persister, isInternal = false)

    val dataSynchroniserFunc =
      new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).persistTeamsAndReposMapping _

    GithubDataSynchroniser(dataSynchroniserFunc)
  }
}

@Singleton
case class GithubApiClientDecorator @Inject()() {
  def githubApiClient(apiUrl: String, apiToken: String) = GithubApiClient(apiUrl, apiToken)
}
