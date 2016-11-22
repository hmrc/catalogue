import java.time.LocalDateTime

import com.google.inject.{AbstractModule, TypeLiteral}
import com.google.inject.name.Names
import play.api.{Configuration, Environment}
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories._
import uk.gov.hmrc.teamsandrepositories.config.{CacheConfig, GithubConfig}

class Module(environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {
    val useMemoryDataCache = configuration.getBoolean("github.integration.enabled").getOrElse(false)
    if (useMemoryDataCache) {
      val githubConfig = new GithubConfig(configuration)

      val gitApiEnterpriseClient = GithubApiClient(githubConfig.githubApiEnterpriseConfig.apiUrl, githubConfig.githubApiEnterpriseConfig.key)

      val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
        new GithubV3RepositoryDataSource(githubConfig, gitApiEnterpriseClient, isInternal = true)

      val gitOpenClient = GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)
      val openTeamsRepositoryDataSource: RepositoryDataSource =
        new GithubV3RepositoryDataSource(githubConfig, gitOpenClient, isInternal = false)

      val mem = new MemoryCachedRepositoryDataSource[Seq[TeamRepositories]](
        CacheConfig,
        new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _,
        LocalDateTime.now
      )

      bind(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]{})
        .annotatedWith(Names.named("memoryCachedDataSource"))
        .toInstance(mem)
    } else {
      val cacheFilename = configuration.getString("cacheFilename").getOrElse(throw new RuntimeException("cacheFilename is not specified for off-line (dev) usage"))
      val file = new FileCachedRepositoryDataSource(cacheFilename)
      bind(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]{})
        .toInstance(file)
    }
  }
}
