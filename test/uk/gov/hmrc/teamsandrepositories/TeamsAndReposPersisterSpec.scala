package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.Future

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

      when(teamsAndReposPersister.getAllTeamAndRepos)
        .thenReturn(Future.successful(List(teamAndRepositories)))

      when(updateTimePersister.get(sut.teamsAndRepositoriesTimestampKeyName))
        .thenReturn(Future.successful(Some(KeyAndTimestamp(sut.teamsAndRepositoriesTimestampKeyName, now))))

      val retVal = sut.getAllTeamAndRepos

      retVal.futureValue._1 shouldBe Seq(teamAndRepositories)
      retVal.futureValue._2.value shouldBe now
    }

    "delegate to teamsAndReposPersister and updateTimePersister for clearAll" in {

      sut.clearAllData

      verify(teamsAndReposPersister, times(1)).clearAllData
      verify(updateTimePersister, times(1)).remove(sut.teamsAndRepositoriesTimestampKeyName)
    }

    "delegate to updateTimePersister for updating timestamp" in {
      val now = LocalDateTime.now

      sut.updateTimestamp(now)

      verify(updateTimePersister, times(1)).update(KeyAndTimestamp(sut.teamsAndRepositoriesTimestampKeyName, now))


    }
  }


}