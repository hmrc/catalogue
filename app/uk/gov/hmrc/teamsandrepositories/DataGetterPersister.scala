package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{Inject, Singleton}
import play.api.Application
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import uk.gov.hmrc.teamsandrepositories.DataGetterPersister.DataLoaderPersisterFunction

import scala.concurrent.Future

trait DataGetterPersister[T] {
  val run: DataLoaderPersisterFunction[T]
}

object DataGetterPersister {
  type DataLoaderPersisterFunction[T] = TeamsAndReposPersister => Future[Seq[T]]
}

@Singleton
class MongoConnector @Inject()(application: Application) {
//  lazy val mongoConnector:MongoConnector = application.injector.instanceOf[ReactiveMongoComponent].mongoConnector

  val db: () => DB = application.injector.instanceOf[ReactiveMongoComponent].mongoConnector.db
}

case class FileDataGetterPersister(run: DataLoaderPersisterFunction[Boolean]) extends DataGetterPersister[Boolean]
case class GithubDataGetterPersister(run: DataLoaderPersisterFunction[Boolean]) extends DataGetterPersister[Boolean]


