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

package uk.gov.hmrc.catalogue.github

trait GithubEndpoints {
  def rootUrl: String

  def reposForTeamEndpoint(teamId: Long): String

  def teamsForOrganisationEndpoint(organisation: String): String

  def organisationsEndpoint: String

  def repoContentsEndPoint(orgName: String, repositoryName: String): String
}

trait GithubEnterpriseApiEndpoints extends GithubEndpoints {
  override val rootUrl: String = GithubConfig.githubEnterpriseCredentials.host

  def reposForTeamEndpoint(teamId: Long) = s"/api/v3/teams/$teamId/repos?per_page=100"

  def teamsForOrganisationEndpoint(organisation: String) = s"/api/v3/orgs/$organisation/teams?per_page=100"

  def organisationsEndpoint = "/api/v3/user/orgs"

  def repoContentsEndPoint(orgName: String, repositoryName: String) = s"/api/v3/repos/$orgName/$repositoryName/contents"
}

trait GithubOpenApiEndpoints extends GithubEndpoints {
  override val rootUrl: String = GithubConfig.githubOpenCredentials.host

  def reposForTeamEndpoint(teamId: Long) = s"/teams/$teamId/repos?per_page=100"

  def teamsForOrganisationEndpoint(organisation: String) = s"/orgs/$organisation/teams?per_page=100"

  def organisationsEndpoint = "/user/orgs"

  def repoContentsEndPoint(orgName: String, repositoryName: String) = s"/repos/$orgName/$repositoryName/contents"
}
