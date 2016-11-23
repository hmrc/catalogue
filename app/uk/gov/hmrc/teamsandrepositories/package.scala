package uk.gov.hmrc

import scala.concurrent.Future

package object teamsandrepositories {
  type DataLoaderFunction[T] = () => Future[T]

}
