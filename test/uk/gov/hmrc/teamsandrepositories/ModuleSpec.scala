/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{Injector, Key, TypeLiteral}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when, _}
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import uk.gov.hmrc.mongo.lock.{LockRepository, MongoLockRepository, MongoLockService}
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.services.{PersistingService, Timestamper}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ModuleSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with Results
    with OptionValues
    with GuiceOneServerPerSuite
    with Eventually {

  val mockMongoLockRepository: MongoLockRepository = mock[MongoLockRepository](RETURNS_DEEP_STUBS)
  val testMongoLocks: MongoLocks = new MongoLocks(mockMongoLockRepository) {
    override val dataReloadLock: MongoLockService = create
    override val jenkinsLock: MongoLockService    = create
    override val metrixLock: MongoLockService     = create

    private def create = new MongoLockService {
      override val lockRepository: LockRepository = mockMongoLockRepository
      override val lockId: String                 = "testLock"
      override val ttl: Duration                  = 20.minutes

      override def attemptLockWithRelease[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
        body.map(Some(_))(ec)

      override def attemptLockWithRefreshExpiry[T](body: => Future[T])(
        implicit ec: ExecutionContext): Future[Option[T]] =
        body.map(Some(_))(ec)
    }
  }

  val testTimestamper = new Timestamper

  val mockSchedulerConfigs: SchedulerConfigs = mock[SchedulerConfigs](RETURNS_DEEP_STUBS)
  val intervalDuration: FiniteDuration       = 100 millisecond

  when(mockSchedulerConfigs.dataReloadScheduler.initialDelay).thenReturn(intervalDuration)
  when(mockSchedulerConfigs.dataReloadScheduler.interval).thenReturn(intervalDuration)
  when(mockSchedulerConfigs.dataReloadScheduler.enabled).thenReturn(true)

  val mockPersistingService: PersistingService = mock[PersistingService]

  when(mockPersistingService.persistTeamRepoMapping(any())).thenReturn(Future.successful(Nil))
  when(mockPersistingService.removeOrphanTeamsFromMongo(any())(any()))
    .thenReturn(Future.successful(Set.empty[String]))

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .overrides(
        bind[SchedulerConfigs].toInstance(mockSchedulerConfigs),
        bind[Timestamper].toInstance(testTimestamper),
        bind[PersistingService].toInstance(mockPersistingService),
        bind[MongoLocks].toInstance(testMongoLocks)
      )
      .overrides(new Module())
      .configure("metrics.jvm" -> false)
      .build()

  "load DataReloadScheduler eagerly which should result in reload of the cache at the configured intervals" in {

    val guiceInjector = app.injector.instanceOf(classOf[Injector])

    val key = Key.get(new TypeLiteral[DataReloadScheduler]() {})

    guiceInjector.getInstance(key).isInstanceOf[DataReloadScheduler] mustBe true
    verify(mockPersistingService, Mockito.timeout(500).atLeast(2)).persistTeamRepoMapping(any())
  }
}
