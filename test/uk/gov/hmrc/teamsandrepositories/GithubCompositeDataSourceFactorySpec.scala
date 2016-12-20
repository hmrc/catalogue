package uk.gov.hmrc.teamsandrepositories

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerTest
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import uk.gov.hmrc.githubclient.GitApiConfig

class GithubCompositeDataSourceFactorySpec extends FunSpec with Matchers with MockitoSugar with LoneElement with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerTest {

  private val githubConfig = mock[GithubConfig]
  private val persister = mock[TeamsAndReposPersister]
  private val connector = mock[MongoConnector]
  private val githubClientDecorator = mock[GithubApiClientDecorator]

  val sut = new GithubCompositeDataSourceFactory(githubConfig, persister, connector, githubClientDecorator)

  describe("buildDataSource") {
    it("should create the right CompositeRepositoryDataSource") {

      val gitApiOpenConfig = mock[GitApiConfig]
      val gitApiEnterpriseConfig = mock[GitApiConfig]

      when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiEnterpriseConfig)
      when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

      when(gitApiEnterpriseConfig.apiUrl).thenReturn("enterprie.com")
      when(gitApiEnterpriseConfig.key).thenReturn("enterprie.key")

      when(gitApiOpenConfig.apiUrl).thenReturn("open.com")
      when(gitApiOpenConfig.key).thenReturn("open.key")

      val compositeRepositoryDataSource = sut.buildDataSource

      verify(gitApiOpenConfig).apiUrl
      verify(gitApiOpenConfig).key
      verify(gitApiEnterpriseConfig).apiUrl
      verify(gitApiEnterpriseConfig).key

      compositeRepositoryDataSource.dataSources.size shouldBe 2

      val internalDataSource: GithubV3RepositoryDataSource = compositeRepositoryDataSource.dataSources(0)
      internalDataSource.isInternal shouldBe true

      val externalDataSource: GithubV3RepositoryDataSource = compositeRepositoryDataSource.dataSources(1)
      externalDataSource.isInternal shouldBe false

    }
  }

}
