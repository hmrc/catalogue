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

class SynchroniserFactorySpec extends FunSpec with Matchers with MockitoSugar with LoneElement with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerTest {

  private val githubConfig = mock[GithubConfig]
  private val persister = mock[TeamsAndReposPersister]
  private val connector = mock[MongoConnector]
  private val githubClientDecorator = mock[GithubApiClientDecorator]

  val sut = SynchroniserFactory(githubConfig, persister, connector, githubClientDecorator)

  describe("aaa") {
    it("bbb") {

      val gitApiConfig = mock[GitApiConfig]
      when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiConfig)

      when(gitApiConfig.apiUrl).thenReturn("xyz")

      val synchroniser: GithubDataSynchroniser = sut.getSynchroniser

      synchroniser
    }
  }

}
