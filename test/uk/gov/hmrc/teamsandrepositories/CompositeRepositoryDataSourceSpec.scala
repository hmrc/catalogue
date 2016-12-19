/*
 * Copyright 2016 HM Revenue & Customs
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

import java.time.LocalDateTime
import java.util.Date

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.{ExecutionContext, Future}

class CompositeRepositoryDataSourceSpec extends WordSpec with MockitoSugar with ScalaFutures with Matchers with DefaultPatienceConfig {

  val now = new Date().getTime

  "Retrieving team repo mappings" should {

    "return the combination of all input sources"  in {

      val teamsList1 = List(
        PersistedTeamAndRepositories("A", List(GitRepository("A_r", "Some Description", "url_A", now, now))),
        PersistedTeamAndRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now))),
        PersistedTeamAndRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now))))

      val teamsList2 = List(
        PersistedTeamAndRepositories("D", List(GitRepository("D_r", "Some Description", "url_D", now, now))),
        PersistedTeamAndRepositories("E", List(GitRepository("E_r", "Some Description", "url_E", now, now))),
        PersistedTeamAndRepositories("F", List(GitRepository("F_r", "Some Description", "url_F", now, now))))

      val dataSource1 = mock[RepositoryDataSource]
      when(dataSource1.persistTeamsAndReposMapping).thenReturn(Future.successful(teamsList1))

      val dataSource2 = mock[RepositoryDataSource]
      when(dataSource2.persistTeamsAndReposMapping).thenReturn(Future.successful(teamsList2))

      val compositeDataSource = new CompositeRepositoryDataSource(List(dataSource1, dataSource2))
      val result = compositeDataSource.persistTeamsAndReposMapping.futureValue

      result.length shouldBe 6
      result should contain (teamsList1.head)
      result should contain (teamsList1(1))
      result should contain (teamsList1(2))
      result should contain (teamsList2.head)
      result should contain (teamsList2(1))
      result should contain (teamsList2(2))
    }

    "combine teams that have the same names in both sources and sort repositories alphabetically"  in {

      val repoAA = GitRepository("A_A", "Some Description", "url_A_A", now, now)
      val repoAB = GitRepository("A_B", "Some Description", "url_A_B", now, now)
      val repoAC = GitRepository("A_C", "Some Description", "url_A_C", now, now)

      val teamsList1 = List(
        PersistedTeamAndRepositories("A", List(repoAC, repoAB)),
        PersistedTeamAndRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now))),
        PersistedTeamAndRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now))))

      val teamsList2 = List(
        PersistedTeamAndRepositories("A", List(repoAA)),
        PersistedTeamAndRepositories("D", List(GitRepository("D_r", "Some Description", "url_D", now, now))))

      val dataSource1 = mock[RepositoryDataSource]
      when(dataSource1.persistTeamsAndReposMapping()).thenReturn(Future.successful(teamsList1))

      val dataSource2 = mock[RepositoryDataSource]
      when(dataSource2.persistTeamsAndReposMapping()).thenReturn(Future.successful(teamsList2))

      val compositeDataSource = new CompositeRepositoryDataSource(List(dataSource1, dataSource2))
      val result = compositeDataSource.persistTeamsAndReposMapping().futureValue

      result.length shouldBe 4
      result.find(_.teamName == "A").get.repositories should contain inOrderOnly (
        repoAA, repoAB, repoAC)

      result should contain (teamsList1(1))
      result should contain (teamsList1(2))
      result should contain (teamsList2(1))

    }
  }
}
