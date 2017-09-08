package uk.gov.hmrc.teamsandrepositories

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Application
import play.api.inject.ApplicationLifecycle
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, MongoLock}
import uk.gov.hmrc.teamsandrepositories.services.GitCompositeDataSource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class DataReloadSchedulerSpec extends PlaySpec with MockitoSugar with Results with OptionValues with OneServerPerSuite with Eventually {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .build()

  val mockCacheConfig = mock[CacheConfig]
  val mockGitCompositeDataSource = mock[GitCompositeDataSource]

  when(mockGitCompositeDataSource.persistTeamRepoMapping_new).thenReturn(Future(Nil))
  when(mockGitCompositeDataSource.removeOrphanTeamsFromMongo(any())).thenReturn(Future(Set.empty[String]))

  when(mockCacheConfig.teamsCacheDuration).thenReturn(100 millisecond)

  val testMongoLock = new MongoLock(mock[MongoConnector]) {
    override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
      body.map(t =>
        Some(t)
      )
  }

  "reload the cache and remove orphan teams at the configured intervals" in {

    val testScheduler =
      new DataReloadScheduler(actorSystem = app.actorSystem,
        applicationLifecycle = app.injector.instanceOf[ApplicationLifecycle],
        githubCompositeDataSource = mockGitCompositeDataSource,
        cacheConfig = mockCacheConfig,
        mongoLock = testMongoLock)

    verify(mockGitCompositeDataSource, Mockito.timeout(500).atLeast(2)).persistTeamRepoMapping
    verify(mockGitCompositeDataSource, Mockito.timeout(500).atLeast(2)).removeOrphanTeamsFromMongo(any())
  }
}
