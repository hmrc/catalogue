package uk.gov.hmrc.teamsandrepositories


import java.io.File

import org.mockito.Mockito
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder


class ModuleSpec extends WordSpec with MockitoSugar with Matchers{

  private val mockConfiguration = mock[Configuration]
  private val mockEnv = mock[play.api.Environment]

  "Play module" should {
    "give File when conf is on " in {

      val tempFile = File.createTempFile("test", "file")


      Mockito.when(mockConfiguration.getBoolean("github.integration.enabled")).thenReturn(Some(false))
      Mockito.when(mockConfiguration.getString("cacheFilename")).thenReturn(Some(tempFile.getAbsolutePath))

      val module = new GuiceApplicationBuilder()
        .bindings(new Module(mockEnv, mockConfiguration)).build()

            module.injector.instanceOf(classOf[CachedRepositoryDataSource[Seq[TeamRepositories]]]).isInstanceOf[FileCachedRepositoryDataSource] shouldBe true

      tempFile.deleteOnExit()


    }

  }



}
