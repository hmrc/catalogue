package uk.gov.hmrc.teamsandrepositories

import akka.actor.ActorSystem
import org.mockito.Mockito
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerTest
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Created by armin.
  */
class ModuleSpec extends WordSpec with MockitoSugar with Matchers{

  private val mockConfiguration = mock[Configuration]
  private val mockActorSystem = mock[ActorSystem]



  "module" should {
    "give type B when conf is on " in {

      Mockito.when(mockConfiguration.getString("c")).thenReturn(Some("dsh"))

      val module = new GuiceApplicationBuilder()
        .bindings(new Module(mockActorSystem, mockConfiguration)).build()

      module.injector.instanceOf(classOf[A]).isInstanceOf[C] shouldBe true



    }

  }



}
