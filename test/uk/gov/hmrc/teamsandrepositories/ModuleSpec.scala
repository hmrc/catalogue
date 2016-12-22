package uk.gov.hmrc.teamsandrepositories


import java.io.File

import com.google.inject.{Injector, Key, TypeLiteral}
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.{Application, Configuration}
import play.api.inject.ApplicationLifecycle
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.inject.bind

class ModuleSpec extends PlaySpec with MockitoSugar with Results with OptionValues with OneServerPerSuite with Eventually {

  val testMongoLock = new MongoLock(mock[MongoConnector]) {
    override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
    body.map(Some(_))
  }


  val mockCacheConfig = mock[CacheConfig]
  when(mockCacheConfig.teamsCacheDuration).thenReturn(100 millisecond)

  val mockCompositeRepositoryDataSource = mock[GitCompositeDataSource]
  when(mockCompositeRepositoryDataSource.persistTeamRepoMapping).thenReturn(Future.successful(Nil))

  implicit override lazy val app: Application =
       new GuiceApplicationBuilder()
         .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
         .overrides(
           bind[CacheConfig].toInstance(mockCacheConfig),
           bind[GitCompositeDataSource].toInstance(mockCompositeRepositoryDataSource),
           bind[MongoLock].toInstance(testMongoLock)
         )
         .overrides(new Module())
         .build()


  "reload the cache at the configured intervals" in {

    val guiceInjector = app.injector.instanceOf(classOf[Injector])

    val key = Key.get(new TypeLiteral[DataReloadScheduler]() {})

    guiceInjector.getInstance(key).isInstanceOf[DataReloadScheduler] mustBe true
    verify(mockCompositeRepositoryDataSource, Mockito.timeout(500).atLeast(2)).persistTeamRepoMapping

  }
}


