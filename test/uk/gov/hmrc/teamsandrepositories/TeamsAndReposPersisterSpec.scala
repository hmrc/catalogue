package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest._
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

/**
  * Created by armin.
  */
class TeamsAndReposPersisterSpec extends WordSpec with Matchers with OptionValues with MockitoSugar with LoneElement with MongoSpecSupport with ScalaFutures with BeforeAndAfterEach with OneAppPerSuite {

  private val teamsAndReposPersister = mock[MongoTeamsAndReposPersister]
  private val updateTimePersister = mock[MongoUpdateTimePersister]

  val teamAndRepositories = PersistedTeamAndRepositories("teamX", Nil)

  val sut = new TeamsAndReposPersister(teamsAndReposPersister, updateTimePersister)

  "TeamsAndReposPersisterSpec" should {
    "delegate to teamsAndReposPersister's update" in {

      sut.update(teamAndRepositories)

      verify(teamsAndReposPersister).update(teamAndRepositories)
    }

    "get the teamRepos and update time together" in {
      val now = LocalDateTime.now

      when(teamsAndReposPersister.getAllTeamAndRepos0)
        .thenReturn(Future.successful(Seq(teamAndRepositories)))

      when(updateTimePersister.get(sut.teamsAndRepositoriesTimestampKeyName))
        .thenReturn(Future.successful(Some(KeyAndTimestamp(sut.teamsAndRepositoriesTimestampKeyName, now))))

      val retVal = sut.getAllTeamAndRepos

      retVal.futureValue._1 shouldBe Seq(teamAndRepositories)
      retVal.futureValue._2.value shouldBe now
    }
  }


}