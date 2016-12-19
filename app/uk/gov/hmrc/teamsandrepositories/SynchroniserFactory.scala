package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig


//!@ test this
@Singleton
case class SynchroniserFactory @Inject()(githubConfig: GithubConfig, persister: TeamsAndReposPersister) {
  def getSynchroniser: GithubDataSynchroniser = {
    val url = githubConfig.githubApiEnterpriseConfig.apiUrl

    val gitApiEnterpriseClient = GithubApiClient(url, githubConfig.githubApiEnterpriseConfig.key)
    val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, persister, isInternal = true)

    val gitOpenClient = GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
    val openTeamsRepositoryDataSource: RepositoryDataSource =
      new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, persister, isInternal = false)

    val dataSynchroniserFunc =
      new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).persistTeamsAndReposMapping _

    GithubDataSynchroniser(dataSynchroniserFunc)
  }
}
