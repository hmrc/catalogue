package uk.gov.hmrc.teamsandrepositories

import java.util.Date

import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerTest
import uk.gov.hmrc.githubclient.{GitApiConfig, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.concurrent.Future
import scala.concurrent.Future.successful

class GitCompositeDataSourceSpec extends FunSpec with Matchers with MockitoSugar with LoneElement with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerTest {

  private val githubConfig = mock[GithubConfig]
  private val persister = mock[TeamsAndReposPersister]
  private val connector = mock[MongoConnector]
  private val githubClientDecorator = mock[GithubApiClientDecorator]

  override protected def beforeEach() = {
    reset(githubConfig)
    reset(persister)
    reset(connector)
    reset(githubClientDecorator)
  }


  describe("buildDataSource") {
    it("should create the right CompositeRepositoryDataSource") {

      val gitApiOpenConfig = mock[GitApiConfig]
      val gitApiEnterpriseConfig = mock[GitApiConfig]

      when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiEnterpriseConfig)
      when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

      val enterpriseUrl = "enterprise.com"
      val enterpriseKey = "enterprise.key"
      when(gitApiEnterpriseConfig.apiUrl).thenReturn(enterpriseUrl)
      when(gitApiEnterpriseConfig.key).thenReturn(enterpriseKey)

      val openUrl = "open.com"
      val openKey = "open.key"
      when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
      when(gitApiOpenConfig.key).thenReturn(openKey)

      val enterpriseGithubClient = mock[GithubApiClient]
      val openGithubClient = mock[GithubApiClient]
      when(githubClientDecorator.githubApiClient(enterpriseUrl, enterpriseKey)).thenReturn(enterpriseGithubClient)
      when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)

      val compositeRepositoryDataSource = new GitCompositeDataSource(githubConfig, persister, connector, githubClientDecorator)

      verify(gitApiOpenConfig).apiUrl
      verify(gitApiOpenConfig).key
      verify(gitApiEnterpriseConfig).apiUrl
      verify(gitApiEnterpriseConfig).key

      compositeRepositoryDataSource.dataSources.size shouldBe 2

      val enterpriseDataSource: GithubV3RepositoryDataSource = compositeRepositoryDataSource.dataSources(0)
      enterpriseDataSource shouldBe compositeRepositoryDataSource.enterpriseTeamsRepositoryDataSource

      val openDataSource: GithubV3RepositoryDataSource = compositeRepositoryDataSource.dataSources(1)
      openDataSource shouldBe compositeRepositoryDataSource.openTeamsRepositoryDataSource
    }
  }


  val now = new Date().getTime

  describe("Retrieving team repo mappings") {

    it("return the combination of all input sources") {

      val teamsList1 = List(
        TeamRepositories("A", List(GitRepository("A_r", "Some Description", "url_A", now, now))),
        TeamRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now))),
        TeamRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now))))

      val teamsList2 = List(
        TeamRepositories("D", List(GitRepository("D_r", "Some Description", "url_D", now, now))),
        TeamRepositories("E", List(GitRepository("E_r", "Some Description", "url_E", now, now))),
        TeamRepositories("F", List(GitRepository("F_r", "Some Description", "url_F", now, now))))

      val dataSource1 = mock[GithubV3RepositoryDataSource]
      when(dataSource1.getTeamRepoMapping).thenReturn(successful(teamsList1))

      val dataSource2 = mock[GithubV3RepositoryDataSource]
      when(dataSource2.getTeamRepoMapping).thenReturn(successful(teamsList2))

      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2)
      val result = compositeDataSource.persistTeamRepoMapping.futureValue

      result.length shouldBe 6
      result should contain(teamsList1.head)
      result should contain(teamsList1(1))
      result should contain(teamsList1(2))
      result should contain(teamsList2.head)
      result should contain(teamsList2(1))
      result should contain(teamsList2(2))
    }

    it("combine teams that have the same names in both sources and sort repositories alphabetically") {

      val repoAA = GitRepository("A_A", "Some Description", "url_A_A", now, now)
      val repoAB = GitRepository("A_B", "Some Description", "url_A_B", now, now)
      val repoAC = GitRepository("A_C", "Some Description", "url_A_C", now, now)

      val teamsList1 = List(
        TeamRepositories("A", List(repoAC, repoAB)),
        TeamRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now))),
        TeamRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now))))

      val teamsList2 = List(
        TeamRepositories("A", List(repoAA)),
        TeamRepositories("D", List(GitRepository("D_r", "Some Description", "url_D", now, now))))

      val dataSource1 = mock[GithubV3RepositoryDataSource]
      when(dataSource1.getTeamRepoMapping).thenReturn(successful(teamsList1))

      val dataSource2 = mock[GithubV3RepositoryDataSource]
      when(dataSource2.getTeamRepoMapping).thenReturn(successful(teamsList2))

      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2)

      val result = compositeDataSource.persistTeamRepoMapping.futureValue

      result.length shouldBe 4
      result.find(_.teamName == "A").get.repositories should contain inOrderOnly(
        repoAA, repoAB, repoAC)

      result should contain(teamsList1(1))
      result should contain(teamsList1(2))
      result should contain(teamsList2(1))

    }

    //!@
    //    it("should collect team names from both sources (mongo and github)") {
    //
    //      val dataSource1 = mock[GithubV3RepositoryDataSource]
    //      val dataSource2 = mock[GithubV3RepositoryDataSource]
    //
    //      when(dataSource1.getTeamsNamesFromMongo).thenReturn(TeamNamesTuple(successful(Set("team-a")), successful(Set("team-a", "team-b"))))
    //
    //      when(dataSource2.getTeamsNamesFromMongo).thenReturn(TeamNamesTuple(successful(Set("team-c")), successful(Set("team-c", "team-d"))))
    //
    //      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2)
    //
    //      val teamNamesTuple = compositeDataSource.buildTeamNamesTuple()
    //
    //      teamNamesTuple.ghNames.value.futureValue shouldBe Set("team-a", "team-c")
    //      teamNamesTuple.mongoNames.value.futureValue shouldBe Set("team-a", "team-b", "team-c", "team-d")
    //    }

    it("should use persister for removing deleted teams") {
      val dataSource1 = mock[GithubV3RepositoryDataSource]
      val dataSource2 = mock[GithubV3RepositoryDataSource]

      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2)

      val teamRepositoriesInMongo = Seq(
        TeamRepositories("team-a", Nil),
        TeamRepositories("team-b", Nil),
        TeamRepositories("team-c", Nil),
        TeamRepositories("team-d", Nil)
      )

      when(persister.getAllTeamAndRepos).thenReturn(Future.successful(teamRepositoriesInMongo, None))
      when(persister.deleteTeams(ArgumentMatchers.any())).thenReturn(Future.successful(Set("abc"))) //!@ use mock?

      compositeDataSource.removeOrphanTeamsFromMongo(Seq(TeamRepositories("team-a", Nil), TeamRepositories("team-c", Nil)))

      verify(persister, Mockito.timeout(1000)).deleteTeams(Set("team-b", "team-d"))
    }


  }

  private def buildCompositeDataSource(dataSource1: GithubV3RepositoryDataSource, dataSource2: GithubV3RepositoryDataSource) = {

    val gitApiOpenConfig = mock[GitApiConfig]
    val gitApiEnterpriseConfig = mock[GitApiConfig]

    when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiEnterpriseConfig)
    when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

    val enterpriseUrl = "enterprise.com"
    val enterpriseKey = "enterprise.key"
    when(gitApiEnterpriseConfig.apiUrl).thenReturn(enterpriseUrl)
    when(gitApiEnterpriseConfig.key).thenReturn(enterpriseKey)

    val openUrl = "open.com"
    val openKey = "open.key"
    when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
    when(gitApiOpenConfig.key).thenReturn(openKey)

    val enterpriseGithubClient = mock[GithubApiClient]
    val openGithubClient = mock[GithubApiClient]
    when(githubClientDecorator.githubApiClient(enterpriseUrl, enterpriseKey)).thenReturn(enterpriseGithubClient)
    when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)


    when(persister.update(ArgumentMatchers.any())).thenAnswer(new Answer[Future[TeamRepositories]] {
      override def answer(invocation: InvocationOnMock): Future[TeamRepositories] = {
        val args = invocation.getArguments()
        Future.successful(args(0).asInstanceOf[TeamRepositories])
      }
    })

    when(persister.updateTimestamp(ArgumentMatchers.any())).thenReturn(Future.successful(true))

    new GitCompositeDataSource(githubConfig, persister, connector, githubClientDecorator) {
      override val dataSources: List[GithubV3RepositoryDataSource] = List(dataSource1, dataSource2)
    }
  }
}
