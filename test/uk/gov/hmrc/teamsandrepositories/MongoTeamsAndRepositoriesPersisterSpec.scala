package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, FunSpec, LoneElement, OptionValues}
import org.scalatestplus.play.{OneAppPerSuite, OneAppPerTest}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global


class MongoTeamsAndRepositoriesPersisterSpec extends UnitSpec with LoneElement with MongoSpecSupport with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerSuite {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder().configure(Map("mongodb.uri" -> "mongodb://localhost:27017/test-teams-and-repositories")).build()

  val mongoTeamsAndReposPersister = app.injector.instanceOf(classOf[MongoTeamsAndRepositoriesPersister])

  override def beforeEach() {
    await(mongoTeamsAndReposPersister.drop)
  }


  "get all" should {
    "be able to add, get all teams and repos and delete everything... Everything!" in {
      val now: LocalDateTime = LocalDateTime.now()
      val gitRepository1 = GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Deployable)
      val gitRepository2 = GitRepository("repo-name2", "Desc2", "url2", 3, 4, true, RepoType.Library)

      val gitRepository3 = GitRepository("repo-name3", "Desc3", "url3", 1, 2, false, RepoType.Deployable)
      val gitRepository4 = GitRepository("repo-name4", "Desc4", "url4", 3, 4, true, RepoType.Library)

      val teamAndRepositories1 = PersistedTeamAndRepositories("test-team1", List(gitRepository1, gitRepository2))
      val teamAndRepositories2 = PersistedTeamAndRepositories("test-team2", List(gitRepository3, gitRepository4))
      await(mongoTeamsAndReposPersister.add(teamAndRepositories1))
      await(mongoTeamsAndReposPersister.add(teamAndRepositories2))

      val all = await(mongoTeamsAndReposPersister.getAllTeamAndRepos)

      all.size shouldBe 2
      val result1: PersistedTeamAndRepositories = all(0)
      val result2: PersistedTeamAndRepositories = all(1)

      result1.teamName shouldBe "test-team1"
      result2.teamName shouldBe "test-team2"

      result1.repositories shouldBe List(gitRepository1, gitRepository2)
      result2.repositories shouldBe List(gitRepository3, gitRepository4)

      await(mongoTeamsAndReposPersister.clearAllData)
      val all2 = await(mongoTeamsAndReposPersister.getAllTeamAndRepos)

      all2.size shouldBe 2
    }
  }

  "update" should {
    "update already existing team" in {

      val now: LocalDateTime = LocalDateTime.now()
      val oneHourLater = now.plusHours(1)

      val gitRepository1 = GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Deployable)
      val gitRepository2 = GitRepository("repo-name2", "Desc2", "url2", 3, 4, true, RepoType.Library)

      val teamAndRepositories1 = PersistedTeamAndRepositories("test-team",  List(gitRepository1))
      await(mongoTeamsAndReposPersister.add(teamAndRepositories1))

      val teamAndRepositories2 = PersistedTeamAndRepositories("test-team", List(gitRepository2))
      await(mongoTeamsAndReposPersister.update(teamAndRepositories2))

      val allUpdated = await(mongoTeamsAndReposPersister.getAllTeamAndRepos)
      allUpdated.size shouldBe 1
      val updatedDeployment: PersistedTeamAndRepositories = allUpdated.loneElement

      updatedDeployment.teamName shouldBe "test-team"
      updatedDeployment.repositories shouldBe List(gitRepository2)

    }

  }


  ""

}