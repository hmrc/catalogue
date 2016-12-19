package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{Inject, Singleton}
import play.api.Application
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import uk.gov.hmrc.teamsandrepositories.DataSynchroniser.DataSynchroniserFunction


import scala.concurrent.Future

trait DataSynchroniser {
  val run: DataSynchroniserFunction
}

object DataSynchroniser {
  type DataSynchroniserFunction = TeamsAndReposPersister => Future[Seq[PersistedTeamAndRepositories]]
}

@Singleton
class MongoConnector @Inject()(application: Application) {
  val db: () => DB = application.injector.instanceOf[ReactiveMongoComponent].mongoConnector.db
}

case class GithubDataSynchroniser(run: DataSynchroniserFunction) extends DataSynchroniser


