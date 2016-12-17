package uk.gov.hmrc.teamsandrepositories

import java.time.{LocalDateTime, ZoneOffset}

import com.google.inject.{Inject, Singleton}
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.teamsandrepositories.FutureHelpers.withTimerAndCounter

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global


case class KeyAndTimestamp(keyName: String, timestamp: LocalDateTime)

object KeyAndTimestamp {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val formats = Json.format[KeyAndTimestamp]
}

@Singleton
case class MongoUpdateTimePersister @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[PersistedTeamAndRepositories, BSONObjectID](
    collectionName = "updateTime",
    mongo = mongoConnector.db,
    domainFormat = PersistedTeamAndRepositories.formats) {

  private val keyFieldName = "keyName"

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq(keyFieldName -> IndexType.Hashed), name = Some(keyFieldName + "Idx")))
      )
    )

  def get(keyName: String): Future[Option[KeyAndTimestamp]] = {
    withTimerAndCounter("mongo.update") {
        collection.find(Json.obj(keyFieldName -> Json.toJson(keyName)))
          .cursor[KeyAndTimestamp]()
          .collect[List]().map(_.headOption)
    }
  }

  def update(keyAndTimestamp: KeyAndTimestamp): Future[Boolean] = {
    //!@
    println(s"---> updating keyAndTimestamp: $keyAndTimestamp")
    withTimerAndCounter("mongo.update") {
      for {
        update <- collection.update(selector = Json.obj(keyFieldName -> Json.toJson(keyAndTimestamp.keyName)), update = keyAndTimestamp, upsert = true)
      } yield update match {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    }
  }
}