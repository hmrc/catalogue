package uk.gov.hmrc.teamsandrepositories


import java.io.File

import com.github.tomakehurst.wiremock.http.RequestMethod._
import com.google.inject.{Guice, Injector, Key, TypeLiteral}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, Matchers, WordSpec}
import org.scalatestplus.play.{OneAppPerSuite, OneServerPerSuite}
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.test.WithApplication

import scala.concurrent.Future


class ModuleSpec
  extends WordSpec
    with MockitoSugar
    with Matchers
//    with OneServerPerSuite
{

  private val mockConfiguration = mock[Configuration]
  private val mockEnv = mock[play.api.Environment]

  "Play module" should {
    "give File when conf is on " in {

      val tempFile = File.createTempFile("test", "file")

      when(mockConfiguration.getBoolean("github.offline.mode")).thenReturn(Some(true))
      when(mockConfiguration.getString("cacheFilename")).thenReturn(Some("/some/tmp/file"))

      val application = new GuiceApplicationBuilder().overrides(new Module(mockEnv, mockConfiguration)).build()

      val guiceInjector = application.injector.instanceOf(classOf[Injector])

      val key = Key.get(new TypeLiteral[() => Future[Seq[TeamRepositories]]]() {})

      val cached = guiceInjector.getInstance(key)

      tempFile.deleteOnExit()
    }

    "produce MemCache Data Source when github integration is enabled via the configuration" in {

      when(mockConfiguration.getMilliseconds("cache.teams.duration")).thenReturn(Some(10000l))
//
      when(mockConfiguration.getBoolean("github.offline.mode")).thenReturn(Some(false))
      when(mockConfiguration.getString("github.open.api")).thenReturn(Some("http://something.open.api"))
      when(mockConfiguration.getString("github.open.api.host")).thenReturn(Some("http://yyz.g1thub.c0m"))
      when(mockConfiguration.getString("github.open.api.user")).thenReturn(None)
      when(mockConfiguration.getString("github.open.api.key")).thenReturn(None)

      when(mockConfiguration.getString("github.enterprise.api")).thenReturn(Some("http://github.enterprise.api1"))
      when(mockConfiguration.getString("github.enterprise.api.host")).thenReturn(Some("http://yyz.g1thub.c0m"))
      when(mockConfiguration.getString("github.enterprise.api.user")).thenReturn(Some("something.enterprise.api3"))
      when(mockConfiguration.getString("github.enterprise.api.key")).thenReturn(Some("something.enterprise.api4"))
      when(mockConfiguration.getString("github.hidden.repositories")).thenReturn(None)
      when(mockConfiguration.getString("github.hidden.teams")).thenReturn(None)

      //      serviceEndpoint(GET, "http://github.enterprise.api2", willRespondWith = (200, None))
      //      serviceEndpoint(GET, "http://github.open.api", willRespondWith = (200, None))

      val application = new GuiceApplicationBuilder().overrides(new Module(mockEnv, mockConfiguration)).build()


      val guiceInjector = application.injector.instanceOf(classOf[Injector])

      val key = Key.get(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]() {})

      val cached = guiceInjector.getInstance(key)

      cached.isInstanceOf[MemoryCachedRepositoryDataSource[Seq[TeamRepositories]]] shouldBe true

    }

  }
//  "Play module" should {
//    "give File when conf is on " in {
//
//      val tempFile = File.createTempFile("test", "file")
//
//      when(mockConfiguration.getBoolean("github.offline.mode")).thenReturn(Some(true))
//      when(mockConfiguration.getString("cacheFilename")).thenReturn(Some(tempFile.getAbsolutePath))
//
//      val application = new GuiceApplicationBuilder().overrides(new Module(mockEnv, mockConfiguration)).build()
//
//      val guiceInjector = application.injector.instanceOf(classOf[Injector])
//
//      val key = Key.get(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]() {})
//
//      val cached = guiceInjector.getInstance(key)
//
//      cached.isInstanceOf[FileCachedRepositoryDataSource] shouldBe true
//
//      tempFile.deleteOnExit()
//    }
//
//    "produce MemCache Data Source when github integration is enabled via the configuration" in {
//
//      when(mockConfiguration.getMilliseconds("cache.teams.duration")).thenReturn(Some(10000l))
////
//      when(mockConfiguration.getBoolean("github.offline.mode")).thenReturn(Some(false))
//      when(mockConfiguration.getString("github.open.api")).thenReturn(Some("http://something.open.api"))
//      when(mockConfiguration.getString("github.open.api.host")).thenReturn(Some("http://yyz.g1thub.c0m"))
//      when(mockConfiguration.getString("github.open.api.user")).thenReturn(None)
//      when(mockConfiguration.getString("github.open.api.key")).thenReturn(None)
//
//      when(mockConfiguration.getString("github.enterprise.api")).thenReturn(Some("http://github.enterprise.api1"))
//      when(mockConfiguration.getString("github.enterprise.api.host")).thenReturn(Some("http://yyz.g1thub.c0m"))
//      when(mockConfiguration.getString("github.enterprise.api.user")).thenReturn(Some("something.enterprise.api3"))
//      when(mockConfiguration.getString("github.enterprise.api.key")).thenReturn(Some("something.enterprise.api4"))
//      when(mockConfiguration.getString("github.hidden.repositories")).thenReturn(None)
//      when(mockConfiguration.getString("github.hidden.teams")).thenReturn(None)
//
//      //      serviceEndpoint(GET, "http://github.enterprise.api2", willRespondWith = (200, None))
//      //      serviceEndpoint(GET, "http://github.open.api", willRespondWith = (200, None))
//
//      val application = new GuiceApplicationBuilder().overrides(new Module(mockEnv, mockConfiguration)).build()
//
//
//      val guiceInjector = application.injector.instanceOf(classOf[Injector])
//
//      val key = Key.get(new TypeLiteral[CachedRepositoryDataSource[Seq[TeamRepositories]]]() {})
//
//      val cached = guiceInjector.getInstance(key)
//
//      cached.isInstanceOf[MemoryCachedRepositoryDataSource[Seq[TeamRepositories]]] shouldBe true
//
//    }
//
//  }


}
