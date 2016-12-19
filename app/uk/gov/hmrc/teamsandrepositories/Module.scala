package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{AbstractModule, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.collection.immutable.Seq
import scala.concurrent.Future

class Module(environment: play.api.Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {


    bind(classOf[ReloadScheduler]).asEagerSingleton()


  }


}

