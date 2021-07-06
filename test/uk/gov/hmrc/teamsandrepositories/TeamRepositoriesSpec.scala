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

import java.time.Instant

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.RepoType.{Library, Other, Prototype, Service}
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import uk.gov.hmrc.teamsandrepositories.controller.model.{Repository, Team}
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories.{DigitalServiceRepository, findDigitalServiceDetails}

import scala.collection.immutable.ListMap

class TeamRepositoriesSpec extends AnyWordSpec with Matchers with OptionValues {
  val now = Instant.now()

  private val description = "Some description"

  "GitRepository.getTeamActivityDatesOfNonSharedRepos" should {
    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {
      val oldestLibraryRepo = GitRepository(
        "repo1",
        "some desc",
        "",
        createdDate        = Instant.ofEpochMilli(1),
        lastActiveDate     = Instant.ofEpochMilli(10),
        // isInternal         = false,
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val oldDeployableRepo = GitRepository(
        "repo2",
        "some desc",
        "",
        createdDate        = Instant.ofEpochMilli(2),
        lastActiveDate     = Instant.ofEpochMilli(20),
        // isInternal         = false,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val newDeployableRepo = GitRepository(
        "repo3",
        "some desc",
        "",
        createdDate        = Instant.ofEpochMilli(3),
        lastActiveDate     = Instant.ofEpochMilli(30),
        // isInternal         = true,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val oldOtherRepoWithLatestActiveDate = GitRepository(
        "repo1",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(2),
        lastActiveDate     = Instant.ofEpochMilli(40),
        // isInternal         = true,
        repoType           = Other,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val repositoriesToIgnore = List.empty

      val result0 = GitRepository.getTeamActivityDatesOfNonSharedRepos(List(newDeployableRepo, oldestLibraryRepo), repositoriesToIgnore)
      result0.firstActiveDate.value shouldBe oldestLibraryRepo.createdDate
      result0.lastActiveDate.value  shouldBe newDeployableRepo.lastActiveDate

      val result1 = GitRepository.getTeamActivityDatesOfNonSharedRepos(List(oldDeployableRepo, oldOtherRepoWithLatestActiveDate), repositoriesToIgnore)
      result1.firstActiveDate.get shouldBe oldDeployableRepo.createdDate
      result1.lastActiveDate.get  shouldBe oldOtherRepoWithLatestActiveDate.lastActiveDate

      val result2 = GitRepository.getTeamActivityDatesOfNonSharedRepos(List.empty, repositoriesToIgnore)
      result2.firstActiveDate shouldBe None
      result2.lastActiveDate  shouldBe None
    }

    "exclude specified repos in calculating activity max and min dates" in {
      val oldLibraryRepo = GitRepository(
        "repo1",
        "some desc",
        "",
        createdDate        = Instant.ofEpochMilli(2),
        lastActiveDate     = Instant.ofEpochMilli(20),
        // isInternal         = false,
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val oldDeployableRepo = GitRepository(
        "repo2",
        "some desc",
        "",
        createdDate        = Instant.ofEpochMilli(3),
        lastActiveDate     = Instant.ofEpochMilli(30),
        // isInternal         = true,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val newLibraryRepo = GitRepository(
        "repo1",
        "some desc",
        "",
        createdDate        = Instant.ofEpochMilli(4),
        lastActiveDate     = Instant.ofEpochMilli(40),
        // isInternal         = false,
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val newDeployableRepo = GitRepository(
        "repo2",
        "some desc",
        "",
        createdDate        = Instant.ofEpochMilli(5),
        lastActiveDate     = Instant.ofEpochMilli(50),
        // isInternal         = true,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val newIgnoreRepo = GitRepository(
        "ignoreRepo",
        "some desc",
        "",
        createdDate        = Instant.ofEpochMilli(1),
        lastActiveDate     = Instant.ofEpochMilli(10000),
        // isInternal         = false,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val repositoriesToIgnore = List("ignoreRepo")

      val result0 = GitRepository.getTeamActivityDatesOfNonSharedRepos(List(oldLibraryRepo, newDeployableRepo, newIgnoreRepo), repositoriesToIgnore)
      result0.firstActiveDate.value shouldBe oldLibraryRepo.createdDate
      result0.lastActiveDate.value  shouldBe newDeployableRepo.lastActiveDate

      val result1 = GitRepository.getTeamActivityDatesOfNonSharedRepos(List(oldDeployableRepo, newLibraryRepo, newIgnoreRepo), repositoriesToIgnore)
      result1.firstActiveDate.get shouldBe oldDeployableRepo.createdDate
      result1.lastActiveDate.get  shouldBe newLibraryRepo.lastActiveDate

      val result2 = GitRepository.getTeamActivityDatesOfNonSharedRepos(List.empty, repositoriesToIgnore)
      result2.firstActiveDate shouldBe None
      result2.lastActiveDate  shouldBe None
    }
  }

  "getServiceRepoDetailsList" should {
    "include repository with type not Deployable as services if one of the repositories with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1",
              "some desc",
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = false,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo2",
              "some desc",
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo1",
              "some desc",
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Other,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo3",
              "some desc",
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          now
        ),
        TeamRepositories(
          "teamNameOther",
          List(
            GitRepository(
              "repo3",
              "some desc",
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )),
          now
        )
      )

      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == Service)

      result.map(_.name)          shouldBe List("repo1", "repo2")
      result.map(_.createdAt)     shouldBe List(now, now)
      result.map(_.lastUpdatedAt) shouldBe List(now, now)
    }
  }

  "getAllRepositories" should {
    "deduplicate results" in {
      val teams = Seq(TeamRepositories(
        "team1",
        List(GitRepository("repo1", "some desc", "", createdDate = now, lastActiveDate = now, repoType = Library, digitalServiceName = None, language = Some("Scala"), archived = false)),
        now),
      TeamRepositories(
        "team2",
        List(GitRepository("repo1", "some desc", "", createdDate = now, lastActiveDate = now, repoType = Library, digitalServiceName = None, language = Some("Scala"), archived = false)),
        now))

      val res = TeamRepositories.getAllRepositories(teams)

      res.length shouldBe 1
    }

    "deduplicate results when there is a last modified mismatch" in {
      val teams = Seq(
        TeamRepositories(
          "team1",
          List(GitRepository("repo1", "some desc", "", createdDate = now, lastActiveDate = now, repoType = Library, digitalServiceName = None, language = Some("Scala"), archived = false)),
          now
        ),
        TeamRepositories(
          "team2",
          List(GitRepository("repo1", "some desc", "", createdDate = now, lastActiveDate = now.minusSeconds(1000), repoType = Library, digitalServiceName = None, language = Some("Scala"), archived = false)),
          now
        )
      )

      val res = TeamRepositories.getAllRepositories(teams)

      res.length shouldBe 1
    }
  }

  "findRepositoryDetails" should {
    "find a repository" in { // todo(konrad) add more initial TeamRepositories as test has little value
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = false,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          now
        ))

      TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(ListMap())) shouldBe defined
    }

    "find a repository where the name has a different case" in {
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = false,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          now
        ))

      val Some(repositoryDetails) =
        TeamRepositories.findRepositoryDetails(teams, "REPO1", UrlTemplates(ListMap()))

      repositoryDetails.name       shouldBe "repo1"
      repositoryDetails.repoType   shouldBe Library
      repositoryDetails.createdAt  shouldBe now
      repositoryDetails.lastActive shouldBe now
    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1-prototype",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = false,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          now
        ),
        TeamRepositories(
          "teamNameOther",
          List(
            GitRepository(
              "repo3",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Other,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )),
          now
        )
      )

      val result = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(ListMap()))
      result shouldBe None
    }
  }

  "getRepositoryToTeamNames" should {
    "group teams by services they own filtering out any duplicates" in {
      val teams = Seq(
        TeamRepositories(
          "team1",
          List(
            GitRepository(
              "repo1",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = false,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo2",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          now
        ),
        TeamRepositories(
          "team2",
          List(
            GitRepository(
              "repo2",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo3",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          now
        ),
        TeamRepositories(
          "team2",
          List(
            GitRepository(
              "repo2",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo3",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          now
        ),
        TeamRepositories(
          "team3",
          List(
            GitRepository(
              "repo3",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo4",
              description,
              "",
              createdDate        = now,
              lastActiveDate     = now,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          now
        )
      )

      val result = TeamRepositories.getRepositoryToTeamNames(teams)

      result should contain("repo1" -> Seq("team1"))
      result should contain("repo2" -> Seq("team1", "team2"))
      result should contain("repo3" -> Seq("team2", "team3"))
      result should contain("repo4" -> Seq("team3"))
    }
  }

  "findDigitalServiceDetails" should {
    "get the correct Digital Service Info" in {
      val digitalServiceName = "DigitalService1"

      val repo1 =
        GitRepository(
          name               = "repo1",
          description        = "n/a",
          url                = "n/a",
          createdDate        = now,
          lastActiveDate     = now,
          repoType           = Library,
          digitalServiceName = Some(digitalServiceName),
          language           = Some("Scala"),
          archived           = false
        )

      val mostRecentTimestamp = repo1.lastActiveDate.plusSeconds(1)

      val repo2 = repo1.copy(
        name           = "repo2",
        repoType       = Service,
        lastActiveDate = mostRecentTimestamp
      )

      val repo3 =
        repo1.copy(
          name               = "repo3",
          digitalServiceName = Some("Unexpected Service Name")
        )

      val repo4 =
        repo1.copy(
          name               = "repo4",
          digitalServiceName = None
        )

      val teamsAndRepositories =
        List(
          TeamRepositories("team1", List(repo1, repo2), now),
          TeamRepositories("team2", List(repo3), now),
          TeamRepositories("team3", List(repo1, repo2, repo3), now),
          TeamRepositories("team4", List(repo2, repo4), now)
        )

      val result = findDigitalServiceDetails(teamsAndRepositories, "DigitalService1")

      result.value.name shouldBe digitalServiceName
      result.value.repositories shouldBe Seq(
        DigitalServiceRepository(
          name          = repo1.name,
          createdAt     = repo1.createdDate,
          lastUpdatedAt = repo1.lastActiveDate,
          repoType      = repo1.repoType,
          teamNames     = Seq("team1", "team3"),
          archived      = false
        ),
        DigitalServiceRepository(
          name          = repo2.name,
          createdAt     = repo2.createdDate,
          lastUpdatedAt = repo2.lastActiveDate,
          repoType      = repo2.repoType,
          teamNames     = Seq("team1", "team3", "team4"),
          archived      = false
        )
      )
      result.value.lastUpdatedAt shouldBe mostRecentTimestamp
    }

    "find the Digital Service when the name is of a different case" in {
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              name               = "repo1",
              description        = description,
              url                = "n/a",
              createdDate        = now,
              lastActiveDate     = now,
              digitalServiceName = Some("DigitalService1"),
              archived           = false
            )
          ),
          now
        ))

      findDigitalServiceDetails(teams, "digitalservice1").value.name shouldBe "DigitalService1"
    }
  }

  "toTeam" should {
    val oldDeployableRepo = GitRepository(
      "repo1",
      description,
      "",
      createdDate        = Instant.ofEpochMilli(1),
      lastActiveDate     = Instant.ofEpochMilli(10),
      // isInternal         = false,
      repoType           = Service,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false
    )

    val newDeployableRepo = GitRepository(
      "repo1",
      description,
      "",
      createdDate        = Instant.ofEpochMilli(2),
      lastActiveDate     = Instant.ofEpochMilli(20),
      // isInternal         = true,
      repoType           = Service,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false
    )

    val newLibraryRepo = GitRepository(
      "repo1",
      description,
      "",
      createdDate        = Instant.ofEpochMilli(3),
      lastActiveDate     = Instant.ofEpochMilli(30),
      // isInternal         = true,
      repoType           = Library,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false
    )

    val newOtherRepo = GitRepository(
      "repo1",
      description,
      "",
      createdDate        = Instant.ofEpochMilli(4),
      lastActiveDate     = Instant.ofEpochMilli(40),
      // isInternal         = true,
      repoType           = Other,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false
    )

    val sharedRepo = GitRepository(
      "sharedRepo1",
      description,
      "",
      createdDate        = Instant.ofEpochMilli(5),
      lastActiveDate     = Instant.ofEpochMilli(50),
      // isInternal         = true,
      repoType           = Other,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false
    )

    "get the max last active and min created at for repositories" in {
      val teamRepository = TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo), now)

      val result = teamRepository.toTeam(repositoriesToIgnore = Nil, includeRepos = true)

      result shouldBe Team(
         name                     = "teamName",
         firstActiveDate          = Some(Instant.ofEpochMilli(1)),
         lastActiveDate           = Some(Instant.ofEpochMilli(20)),
         firstServiceCreationDate = Some(oldDeployableRepo.createdDate),
         repos                    = Some(Map(Service -> List("repo1"), Library -> List(), Prototype -> List(), Other -> List()))
       )
    }

    "Include all repository types when get the max last active and min created at for team" in {
      val teamRepository = TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newOtherRepo), now)

      val result = teamRepository.toTeam(repositoriesToIgnore = Nil, includeRepos = true)

      result shouldBe Team(
        "teamName",
        Some(Instant.ofEpochMilli(1)),
        Some(Instant.ofEpochMilli(40)),
        Some(oldDeployableRepo.createdDate),
        Some(
          Map(
            Service   -> List("repo1"),
            Library   -> List("repo1"),
            Prototype -> List(),
            Other     -> List("repo1")
          ))
      )
    }

    "populate firstServiceCreation date by looking at only the service repository" in {
      val teamRepository = TeamRepositories(
        "teamName",
        List(newDeployableRepo, oldDeployableRepo, newLibraryRepo, newOtherRepo, sharedRepo),
        now
      )

      val result = teamRepository.toTeam(repositoriesToIgnore = List("sharedRepo1", "sharedRepo2", "sharedRepo3"), includeRepos = true)

      result shouldBe Team(
        "teamName",
        Some(Instant.ofEpochMilli(1)),
        Some(Instant.ofEpochMilli(40)),
        Some(oldDeployableRepo.createdDate),
        Some(
          Map(
            Service   -> List("repo1"),
            Library   -> List("repo1"),
            Prototype -> List(),
            Other     -> List("repo1", "sharedRepo1")
          ))
      )
    }

    "get all teams and their repositories grouped by repo type" in {

      val repo1 = GitRepository(
        "repo1",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(1),
        lastActiveDate     = Instant.ofEpochMilli(10),
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        owningTeams        = List("teamName"),
        archived           = false
      )

      val repo2 = GitRepository(
        "repo2",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(1),
        lastActiveDate     = Instant.ofEpochMilli(10),
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val repo3 = GitRepository(
        "repo3",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(2),
        lastActiveDate     = Instant.ofEpochMilli(20),
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val repo4 = GitRepository(
        "repo4",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(2),
        lastActiveDate     = Instant.ofEpochMilli(20),
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val repo5 = GitRepository(
        "repo5",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(3),
        lastActiveDate     = Instant.ofEpochMilli(30),
        repoType           = Other,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val repo6 = GitRepository(
        "repo6",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(3),
        lastActiveDate     = Instant.ofEpochMilli(30),
        repoType           = Other,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val repo7 = GitRepository(
        "repo7",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(4),
        lastActiveDate     = Instant.ofEpochMilli(40),
        repoType           = Prototype,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val repo8 = GitRepository(
        "repo8",
        description,
        "",
        createdDate        = Instant.ofEpochMilli(4),
        lastActiveDate     = Instant.ofEpochMilli(40),
        repoType           = Prototype,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val teamRepository =
        TeamRepositories("teamName", List(repo1, repo2, repo3, repo4, repo5), now)

      teamRepository.toTeam(repositoriesToIgnore = Nil, includeRepos = true) shouldEqual Team(
          name                     = "teamName",
          firstActiveDate          = Some(Instant.ofEpochMilli(1)),
          lastActiveDate           = Some(Instant.ofEpochMilli(30)),
          firstServiceCreationDate = Some(Instant.ofEpochMilli(1)),
          repos = Some(
            Map(
              Service   -> List("repo1", "repo2"),
              Library   -> List("repo3", "repo4"),
              Prototype -> List(),
              Other     -> List("repo5"))),
          ownedRepos = List("repo1")
        )

      val teamOtherRepository =
        TeamRepositories("teamNameOther", List(repo4, repo5, repo6, repo7, repo8), now)

      teamOtherRepository.toTeam(repositoriesToIgnore = Nil, includeRepos = true) shouldEqual Team(
        name                     = "teamNameOther",
        firstActiveDate          = Some(Instant.ofEpochMilli(2)),
        lastActiveDate           = Some(Instant.ofEpochMilli(40)),
        firstServiceCreationDate = None,
        repos = Some(
          Map(
            Service   -> List(),
            Library   -> List("repo4"),
            Prototype -> List("repo7", "repo8"),
            Other     -> List("repo5", "repo6"))
        )
      )
    }
  }
}
