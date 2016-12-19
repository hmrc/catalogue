package uk.gov.hmrc.teamsandrepositories

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import play.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReloadScheduler @Inject()(val actorSystem: ActorSystem,
                                applicationLifecycle: ApplicationLifecycle,
                                synchroniserFactory: SynchroniserFactory,
                                cacheConfig: CacheConfig)(implicit ec: ExecutionContext) {

  private val scheduledReload = actorSystem.scheduler.schedule(cacheConfig.teamsCacheDuration, cacheConfig.teamsCacheDuration) {
    Logger.info("Scheduled teams repository cache reload triggered")
    //!@ put the teamsAndReposPersister in syncher
    synchroniserFactory.getSynchroniser.run()
  }

  applicationLifecycle.addStopHook(() => Future(scheduledReload.cancel()))
}
