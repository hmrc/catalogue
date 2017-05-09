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

import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date

import org.scalatest.{Matchers, OptionValues, WordSpec}
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates

import scala.collection.immutable.ListMap

class TeamRepositoriesSpec extends WordSpec with Matchers with OptionValues{

  val timestamp = new Date().getTime
  val now = LocalDateTime.now()
  val nowInMillis = now.toInstant(ZoneOffset.UTC).toEpochMilli


  private val createdDateForDeployable1 = 1
  private val createdDateForDeployable2 = 2
  private val createdDateForLib1 = 3
  private val createdDateForLib2 = 4
  private val createdDateForLib3 = 5

  private val lastActiveDateForDeployable1 = 10
  private val lastActiveDateForDeployable2 = 20
  private val lastActiveDateForLib1 = 30
  private val lastActiveDateForLib2 = 40
  private val lastActiveDateForLib3 = 50

  private val createdDateForOther = 111111123l
  private val lastActiveDateForOther = 111111124l

  "getTeamList" should {

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 10, digitalServiceName = None)
      val oldDeployableRepo = GitRepository("repo2", "some desc", "", isInternal = false, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20, digitalServiceName = None)
      val newDeployableRepo = GitRepository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = 3, lastActiveDate = 30, digitalServiceName = None)
      val oldOtherRepoWithLatestActiveDate = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 2, lastActiveDate = 40, digitalServiceName = None)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(newDeployableRepo, oldestLibraryRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo, oldOtherRepoWithLatestActiveDate)),
        TeamRepositories("teamNameNotActive", List())
      )

      val result: Seq[Team] = TeamRepositories.getTeamList(teams, Nil)

      result(0).name shouldBe "teamNameChicken"
      result(0).firstActiveDate.get shouldBe oldestLibraryRepo.createdDate
      result(0).lastActiveDate.get shouldBe newDeployableRepo.lastActiveDate

      result(1).name shouldBe "teamName"
      result(1).firstActiveDate.get shouldBe oldDeployableRepo.createdDate
      result(1).lastActiveDate.get shouldBe oldOtherRepoWithLatestActiveDate.lastActiveDate

      result(2).name shouldBe "teamNameNotActive"
      result(2).firstActiveDate shouldBe None
      result(2).lastActiveDate shouldBe None

    }

    "Exclude specified repos in calculating activity max and min dates" in {

      val oldLibraryRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 2, lastActiveDate = 20, digitalServiceName = None)
      val oldDeployableRepo = GitRepository("repo2", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = 3, lastActiveDate = 30, digitalServiceName = None)
      val newLibraryRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 4, lastActiveDate = 40, digitalServiceName = None)
      val newDeployableRepo = GitRepository("repo2", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = 5, lastActiveDate = 50, digitalServiceName = None)
      val newIgnoreRepo = GitRepository("ignoreRepo", "some desc", "", isInternal = false, repoType = RepoType.Service, createdDate = 1, lastActiveDate = 10000, digitalServiceName = None)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(oldLibraryRepo, newDeployableRepo, newIgnoreRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newIgnoreRepo)),
        TeamRepositories("teamNameNotActive", List())
      )

      val result: Seq[Team] = TeamRepositories.getTeamList(teams, List("ignoreRepo"))

      result(0).name shouldBe "teamNameChicken"
      result(0).firstActiveDate.get shouldBe oldLibraryRepo.createdDate
      result(0).lastActiveDate.get shouldBe newDeployableRepo.lastActiveDate

      result(1).name shouldBe "teamName"
      result(1).firstActiveDate.get shouldBe oldDeployableRepo.createdDate
      result(1).lastActiveDate.get shouldBe newLibraryRepo.lastActiveDate

      result(2).name shouldBe "teamNameNotActive"
      result(2).firstActiveDate shouldBe None
      result(2).lastActiveDate shouldBe None

    }

  }

  "getServiceRepoDetailsList" should {

    "include repository with type not Deployable as services if one of the repositories with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo2", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo1", "some desc", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)
        )
        ),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))
        )
      )

      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Service)

      result.map(_.name) shouldBe List("repo1", "repo2")
      result.map(_.createdAt) shouldBe List(timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp)
    }


    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 10, digitalServiceName = None)
      val oldDeployableRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20, digitalServiceName = None)
      val newDeployableRepo = GitRepository("repo1", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = 3, lastActiveDate = 30, digitalServiceName = None)
      val newestOtherRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 4, lastActiveDate = 40, digitalServiceName = None)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(oldestLibraryRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo)),
        TeamRepositories("teamNameOther", List(newestOtherRepo))
      )

      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Service)

      result.map(_.name) shouldBe List("repo1")
      result.map(_.createdAt) shouldBe List(1)
      result.map(_.lastUpdatedAt) shouldBe List(40)

    }
  }

  "getLibraryRepoDetailsList" should {
    "not include libraries if one of the repository with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1, digitalServiceName = None),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = createdDateForDeployable2, lastActiveDate = lastActiveDateForDeployable2, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2, digitalServiceName = None)
        )
        ),
        TeamRepositories("teamNameOther", List(GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Library)

      result.map(_.name) shouldBe List("repo3", "repo4")

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2, digitalServiceName = None)
        )
        )
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Library)

      result.map(_.name) shouldBe List("repo1")
      result.map(_.createdAt) shouldBe List(createdDateForLib1)
      result.map(_.lastUpdatedAt) shouldBe List(lastActiveDateForLib2)
    }

    "include as library even if one of the repository with same name is Other" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)
        )
        ),
        TeamRepositories("teamNameOther", List(GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Library)

      result.map(_.name) shouldBe List("repo1", "repo3", "repo4")
      result.map(_.createdAt) shouldBe List(timestamp, timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp, timestamp)
    }

  }

  "findRepositoryDetails" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)
        )
        ),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))
        )
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe RepoType.Service

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 10, digitalServiceName = None),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)
        )
        ),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 3, lastActiveDate = 30, digitalServiceName = None))
        )
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      val repositoryDetails: RepositoryDetails = result.get
      repositoryDetails.name shouldBe "repo1"
      repositoryDetails.repoType shouldBe RepoType.Service
      repositoryDetails.createdAt shouldBe 1
      repositoryDetails.lastActive shouldBe 30

    }

    "find repository as type Library even if one of the repo with same name is not type library" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)
        )
        ),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))
        ),
        TeamRepositories("teamNameOther1", List(GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe RepoType.Library
      result.get.teamNames shouldBe List("teamName", "teamNameOther", "teamNameOther1")
      result.get.githubUrls.size shouldBe 2

    }


    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1-prototype", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)
        )),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
      )

      val result = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))
      result shouldBe None
    }

  }


  "getTeamRepositoryNameList" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)
        )
        ),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
      )
      val result = TeamRepositories.getTeamRepositoryNameList(teams, "teamName")

      result shouldBe Some(Map(RepoType.Service -> List("repo1", "repo2"), RepoType.Library -> List("repo3"), RepoType.Prototype -> List(), RepoType.Other -> List()))
    }


  }

  "asRepositoryTeamNameList" should {

    "group teams by services they own filtering out any duplicates" in {

      val teams = Seq(
        TeamRepositories("team1", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))),
        TeamRepositories("team2", List(
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))),
        TeamRepositories("team2", List(
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))),
        TeamRepositories("team3", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))))

      val result = TeamRepositories.getRepositoryToTeamNameList(teams)

      result should contain("repo1" -> Seq("team1"))
      result should contain("repo2" -> Seq("team1", "team2"))
      result should contain("repo3" -> Seq("team2", "team3"))
      result should contain("repo4" -> Seq("team3"))

    }

  }


  "findTeam" should {

    val oldDeployableRepo = GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = 1, lastActiveDate = 10, digitalServiceName = None)
    val newDeployableRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20, digitalServiceName = None)
    val newLibraryRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = 3, lastActiveDate = 30, digitalServiceName = None)
    val newOtherRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 4, lastActiveDate = 40, digitalServiceName = None)
    val sharedRepo = GitRepository("sharedRepo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 5, lastActiveDate = 50, digitalServiceName = None)

    val teams = Seq(
      TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo)),
      TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
    )


    "get the max last active and min created at for repositories with the same name" in {
      val result = TeamRepositories.findTeam(teams, "teamName", Nil)

      val oldDeployableRepo = GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = 1, lastActiveDate = 10, digitalServiceName = None)
      val newDeployableRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20, digitalServiceName = None)

      result.value shouldBe Team(name = "teamName", firstActiveDate = Some(1), lastActiveDate = Some(20), firstServiceCreationDate = Some(oldDeployableRepo.createdDate),
        repos = Seq(Repository("repo1", 1, 20, RepoType.Service)))
    }

    "Include all repository types when get the max last active and min created at for team" in {

      val teams = Seq(
        TeamRepositories("teamName", List(oldDeployableRepo.copy(name = "A"), newLibraryRepo.copy(name = "B"), newOtherRepo.copy(name = "C"))),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
      )

      val result = TeamRepositories.findTeam(teams, "teamName", Nil)

      result.value shouldBe
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Seq(Repository("A", 1, 10, RepoType.Service),
              Repository("B", 3, 30, RepoType.Service),
              Repository("C", 4, 40, RepoType.Service))
        )

    }

    "Exclude all shared repositories when calculating the min and max activity dates for a team" in {

      val teams = Seq(
//        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newOtherRepo, sharedRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo.copy(name = "A"), newLibraryRepo.copy(name = "B"), newOtherRepo.copy(name = "C"), sharedRepo)),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
      )

      val result = TeamRepositories.findTeam(teams, "teamName", List("sharedRepo1", "sharedRepo2", "sharedRepo3"))

      result.value shouldBe
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Seq(Repository("A", 1, 10, RepoType.Service),
              Repository("B", 3, 30, RepoType.Service),
              Repository("C", 4, 40, RepoType.Service),
              Repository(sharedRepo.name, 5, 50, RepoType.Other))
        )
    }


    "populate firstServiceCreation date by looking at only the service repository" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          newDeployableRepo.copy(name="A"),
          oldDeployableRepo.copy(name = "B"),
          newLibraryRepo.copy(name = "C"),
          newOtherRepo.copy(name = "D"),
          sharedRepo)),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None)))
      )

      val result = TeamRepositories.findTeam(teams, "teamName", List("sharedRepo1", "sharedRepo2", "sharedRepo3"))


      result.value shouldBe
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Seq(Repository("A", 2, 20, RepoType.Service),
              Repository("B", 1, 10, RepoType.Service),
              Repository("C", 3, 30, RepoType.Library),
              Repository("D", 4, 40, RepoType.Other),
              Repository("sharedRepo1", 5, 50, RepoType.Other))
        )
    }


    "return None when queried with a non existing team" in {
      TeamRepositories.findTeam(teams, "nonExistingTeam", Nil) shouldBe None
    }

  }

  "findDigitalServiceDetails" should {

    "get the right Digital Service information" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService1")),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService1"))
        )),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService1")),
          GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService2")))
        ),
        TeamRepositories("teamNameOtherOne", List(
          GitRepository("repo5", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService3")),
          GitRepository("repo6", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService3")))
        )
      )
      val result: Option[TeamRepositories.DigitalService] = TeamRepositories.findDigitalServiceDetails(teams, "DigitalService1")

      result.value.name shouldBe "DigitalService1"
      result.value.repositories shouldBe Seq(
        Repository("repo1", timestamp, nowInMillis, RepoType.Library),
        Repository("repo2", timestamp, nowInMillis, RepoType.Service),
        Repository("repo3", timestamp, nowInMillis, RepoType.Library)
      )
      result.value.lastUpdatedAt shouldBe nowInMillis
    }

    "get the lastUpdated timestamp for a Digital Service" in {
      val lastUpdatedTimestamp1 = nowInMillis
      val lastUpdatedTimestamp2 = nowInMillis + 100
      val lastUpdatedTimestamp3 = nowInMillis + 200

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = lastUpdatedTimestamp1, digitalServiceName = Some("DigitalService1")),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = lastUpdatedTimestamp2, digitalServiceName = Some("DigitalService1")),
          GitRepository("repo3", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = lastUpdatedTimestamp3, digitalServiceName = Some("DigitalService1"))
        ))
      )
      val result: Option[TeamRepositories.DigitalService] = TeamRepositories.findDigitalServiceDetails(teams, "DigitalService1")

      result.value.name shouldBe "DigitalService1"
      result.value.repositories should contain theSameElementsAs Seq(
        Repository("repo1", timestamp, lastUpdatedTimestamp1, RepoType.Library),
        Repository("repo3", timestamp, lastUpdatedTimestamp3, RepoType.Library),
        Repository("repo2", timestamp, lastUpdatedTimestamp2, RepoType.Service)
      )
      result.value.lastUpdatedAt shouldBe lastUpdatedTimestamp3
    }

    "get the correct repo types for Digital Service information" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService1")),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService1"))
        )),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Prototype, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService1")),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = nowInMillis, digitalServiceName = Some("DigitalService2")))
        )
      )
      val result: Option[TeamRepositories.DigitalService] = TeamRepositories.findDigitalServiceDetails(teams, "DigitalService1")

      result.value.name shouldBe "DigitalService1"
      result.value.repositories shouldBe Seq(
        Repository("repo1", timestamp, nowInMillis, RepoType.Prototype)
      )
      result.value.lastUpdatedAt shouldBe nowInMillis
    }



  }



  "getAllRepositories" should {
    "discard duplicate repositories according to the repository type configured hierarchy" in {

      val teams = Seq(
        TeamRepositories("team1", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))),
        TeamRepositories("team2", List(
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))),
        TeamRepositories("team2", List(
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))),
        TeamRepositories("team3", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None),
          GitRepository("repo5-prototype", "Some description", "", isInternal = true, repoType = RepoType.Prototype, createdDate = timestamp, lastActiveDate = timestamp, digitalServiceName = None))))

      TeamRepositories.getAllRepositories(teams) shouldBe Seq(
        Repository(name = "repo1", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Service),
        Repository(name = "repo2", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Library),
        Repository(name = "repo3", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Library),
        Repository(name = "repo4", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Other),
        Repository(name = "repo5-prototype", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Prototype)
      )

    }

  }
}
