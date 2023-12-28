/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.buildstate;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_BUILD_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_AUTHOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_AUTHOR_AVATAR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_AUTHOR_EMAIL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_AUTHOR_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_MESSAGE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REMOTE_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REPO;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REPO_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REPO_REMOTE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_TRIGGER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_AVATAR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_EMAIL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BEFORE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_MESSAGE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_GIT_SSH_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_PULL_REQUEST;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_PULL_REQUEST_TITLE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_NAMESPACE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_OWNER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_PRIVATE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_VISIBILITY;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SOURCE_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_KIND;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SYSTEM_HOST;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SYSTEM_HOSTNAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SYSTEM_PROTO;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TARGET_BRANCH;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.ConnectorType.HARNESS;
import static io.harness.delegate.beans.connector.scm.adapter.AzureRepoToGitMapper.mapToGitConnectionType;

import static java.lang.String.format;

import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.ci.execution.execution.GitBuildStatusUtility;
import io.harness.ci.execution.integrationstage.BuildEnvironmentUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.utils.CiCodebaseUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class CodebaseUtils {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private GitBuildStatusUtility gitBuildStatusUtility;
  @Inject private CiCodebaseUtils ciCodebaseUtils;
  private final String PRMergeStatus = "merged";

  public Map<String, String> getCodebaseVars(
      Ambiance ambiance, CIExecutionArgs ciExecutionArgs, ConnectorDetails gitConnectorDetails) {
    Map<String, String> envVars = BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs);
    envVars.putAll(getRuntimeCodebaseVars(ambiance, gitConnectorDetails));
    return envVars;
  }

  private static Map<String, String> getDroneSystemEnvVars() {
    Map<String, String> envVarMap = new HashMap<>();
    // Added this caused regression since users can rely on CI env value to do some operations.
    // envVarMap.put(CI, "true"); // hardcoded to true in drone
    envVarMap.put(DRONE, "true"); // actually false but possibly required in some plugin - hardcoded to true in drone
    envVarMap.put(DRONE_STAGE_KIND, "pipeline");

    return envVarMap;
  }

  public Map<String, String> getRuntimeCodebaseVars(Ambiance ambiance, ConnectorDetails gitConnectorDetails) {
    Map<String, String> codebaseRuntimeVars = new HashMap<>();

    codebaseRuntimeVars.putAll(getDroneSystemEnvVars());

    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
    if (!optionalSweepingOutput.isFound()) {
      return codebaseRuntimeVars;
    }

    CodebaseSweepingOutput codebaseSweeping = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();
    String commitSha = codebaseSweeping.getCommitSha();

    /* Bitbucket SAAS does not generate refs/pull-requests/* which requires us to do this special handling.
      Override commit ref to source branch instead of pull request ref. Same is true for some versions of
      bitbucket server too.
     */
    String commitRef = codebaseSweeping.getCommitRef();
    if (gitConnectorDetails != null && gitConnectorDetails.getConnectorType() == BITBUCKET) {
      if (isNotEmpty(codebaseSweeping.getState()) && codebaseSweeping.getState().equals(PRMergeStatus)) {
        commitRef = format("+refs/heads/%s", codebaseSweeping.getTargetBranch());
        if (isNotEmpty(codebaseSweeping.getMergeSha())) {
          commitSha = codebaseSweeping.getMergeSha();
        }
      } else {
        commitRef = format("+refs/heads/%s", codebaseSweeping.getSourceBranch());
      }
    }

    String commitMessage = codebaseSweeping.getCommitMessage();
    if (isNotEmpty(commitMessage)) {
      codebaseRuntimeVars.put(DRONE_COMMIT_MESSAGE, commitMessage);
      codebaseRuntimeVars.put(CI_COMMIT_MESSAGE, commitMessage);
    }

    String buildLink = gitBuildStatusUtility.getBuildDetailsUrl(ambiance);
    if (isNotEmpty(buildLink)) {
      codebaseRuntimeVars.put(DRONE_BUILD_LINK, buildLink);
      codebaseRuntimeVars.put(CI_BUILD_LINK, buildLink);
      try {
        URL url = new URL(buildLink);
        codebaseRuntimeVars.put(DRONE_SYSTEM_PROTO, url.getProtocol());
        codebaseRuntimeVars.put(DRONE_SYSTEM_HOST, url.getHost());
        codebaseRuntimeVars.put(DRONE_SYSTEM_HOSTNAME, url.getHost());
      } catch (Exception e) {
        log.error("Failed tp parse Drone URL env vars", e);
        e.printStackTrace();
      }
    }

    if (isNotEmpty(codebaseSweeping.getPrTitle())) {
      codebaseRuntimeVars.put(DRONE_PULL_REQUEST_TITLE, codebaseSweeping.getPrTitle());
    }

    if (isNotEmpty(commitRef)) {
      codebaseRuntimeVars.put(DRONE_COMMIT_REF, commitRef);
      codebaseRuntimeVars.put(CI_COMMIT_REF, commitRef);
    }

    if (isNotEmpty(codebaseSweeping.getBranch())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_BRANCH, codebaseSweeping.getBranch());
      codebaseRuntimeVars.put(CI_COMMIT_BRANCH, codebaseSweeping.getBranch());
      codebaseRuntimeVars.put(DRONE_REPO_BRANCH, codebaseSweeping.getBranch());
      codebaseRuntimeVars.put(DRONE_BRANCH, codebaseSweeping.getBranch());
      codebaseRuntimeVars.put(DRONE_SOURCE_BRANCH, codebaseSweeping.getBranch());
      codebaseRuntimeVars.put(DRONE_TARGET_BRANCH, codebaseSweeping.getBranch());
      codebaseRuntimeVars.put(DRONE_BUILD_EVENT, "push");
    }

    if (codebaseSweeping.getBuild() != null && isNotEmpty(codebaseSweeping.getBuild().getType())
        && codebaseSweeping.getBuild().getType().equals("PR")) {
      codebaseRuntimeVars.put(DRONE_BUILD_EVENT, "pull_request");
      codebaseRuntimeVars.put(CI_BUILD_EVENT, "pull_request");
    }
    codebaseRuntimeVars.put(DRONE_REPO_PRIVATE, "true");
    codebaseRuntimeVars.put(DRONE_REPO_VISIBILITY, "private");

    if (!isEmpty(codebaseSweeping.getTag())) {
      codebaseRuntimeVars.put(DRONE_TAG, codebaseSweeping.getTag());
      codebaseRuntimeVars.put(DRONE_BUILD_EVENT, "tag");
      codebaseRuntimeVars.put(CI_BUILD_EVENT, "tag");
    }

    if (!isEmpty(codebaseSweeping.getTargetBranch())) {
      codebaseRuntimeVars.put(DRONE_TARGET_BRANCH, codebaseSweeping.getTargetBranch());
    }
    if (!isEmpty(codebaseSweeping.getCommits())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_LINK, codebaseSweeping.getCommits().get(0).getLink());
    }
    if (!isEmpty(codebaseSweeping.getPullRequestLink())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_LINK, codebaseSweeping.getPullRequestLink());
    }

    if (!isEmpty(codebaseSweeping.getSourceBranch())) {
      codebaseRuntimeVars.put(DRONE_SOURCE_BRANCH, codebaseSweeping.getSourceBranch());
    }

    if (!isEmpty(codebaseSweeping.getGitUserEmail())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_AUTHOR_EMAIL, codebaseSweeping.getGitUserEmail());
      codebaseRuntimeVars.put(CI_COMMIT_AUTHOR_EMAIL, codebaseSweeping.getGitUserEmail());
    }
    if (!isEmpty(codebaseSweeping.getGitUserAvatar())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_AUTHOR_AVATAR, codebaseSweeping.getGitUserAvatar());
      codebaseRuntimeVars.put(CI_COMMIT_AUTHOR_AVATAR, codebaseSweeping.getGitUserAvatar());
    }

    if (!isEmpty(codebaseSweeping.getGitUserId())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_AUTHOR, codebaseSweeping.getGitUserId());
      codebaseRuntimeVars.put(CI_COMMIT_AUTHOR, codebaseSweeping.getGitUserId());
      codebaseRuntimeVars.put(DRONE_REPO_NAMESPACE, codebaseSweeping.getGitUserId());
      codebaseRuntimeVars.put(DRONE_REPO_OWNER, codebaseSweeping.getGitUserId());
    }
    if (!isEmpty(codebaseSweeping.getGitUser())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_AUTHOR_NAME, codebaseSweeping.getGitUser());
      codebaseRuntimeVars.put(CI_COMMIT_AUTHOR_NAME, codebaseSweeping.getGitUser());
      codebaseRuntimeVars.put(DRONE_BUILD_TRIGGER, codebaseSweeping.getGitUser());
    }

    if (isNotEmpty(codebaseSweeping.getBaseCommitSha())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_BEFORE, codebaseSweeping.getBaseCommitSha());
    }
    if (isNotEmpty(commitSha)) {
      codebaseRuntimeVars.put(DRONE_COMMIT_SHA, commitSha);
      codebaseRuntimeVars.put(DRONE_COMMIT, commitSha);
      codebaseRuntimeVars.put(CI_COMMIT_SHA, commitSha);
    }
    if (isNotEmpty(codebaseSweeping.getRepoUrl())) {
      codebaseRuntimeVars.put(DRONE_REPO_LINK, codebaseSweeping.getRepoUrl());
      codebaseRuntimeVars.put(CI_REPO_LINK, codebaseSweeping.getRepoUrl());
      codebaseRuntimeVars.put(CI_REPO_REMOTE, codebaseSweeping.getRepoUrl());
      codebaseRuntimeVars.put(CI_REMOTE_URL, codebaseSweeping.getRepoUrl());
      codebaseRuntimeVars.put(DRONE_GIT_SSH_URL, transformToSshUrl(codebaseSweeping.getRepoUrl()));

      String repoName = GitClientHelper.getGitRepo(codebaseSweeping.getRepoUrl());
      codebaseRuntimeVars.put(DRONE_REPO_NAME, repoName);
      codebaseRuntimeVars.put(CI_REPO, repoName);

      if (!isEmpty(codebaseSweeping.getGitUserId())) {
        codebaseRuntimeVars.put(DRONE_REPO, codebaseSweeping.getGitUserId() + "/" + repoName);
      }
    }
    if (isNotEmpty(codebaseSweeping.getPrNumber())) {
      codebaseRuntimeVars.put(DRONE_PULL_REQUEST, codebaseSweeping.getPrNumber());
    }

    return codebaseRuntimeVars;
  }

  public Map<String, String> getGitEnvVariables(
      ConnectorDetails gitConnector, CodeBase ciCodebase, boolean skipGitClone) {
    if (skipGitClone) {
      return new HashMap<>();
    }
    if (ciCodebase == null) {
      throw new CIStageExecutionException("CI codebase spec is not set");
    }
    String repoName = ciCodebase.getRepoName().getValue();
    return ciCodebaseUtils.getGitEnvVariables(gitConnector, repoName);
  }

  public ConnectorDetails getGitConnector(
      NGAccess ngAccess, CodeBase codeBase, boolean skipGitClone, Ambiance ambiance) {
    if (skipGitClone) {
      return null;
    }

    if (codeBase == null) {
      throw new CIStageExecutionException("CI codebase is mandatory in case git clone is enabled");
    }

    String connectorRefValue = RunTimeInputHandler.resolveString(codeBase.getConnectorRef());
    String repoName = RunTimeInputHandler.resolveString(codeBase.getRepoName());

    return getGitConnector(ngAccess, connectorRefValue, ambiance, repoName);
  }

  public ConnectorDetails getGitConnector(
      NGAccess ngAccess, String gitConnectorRefValue, Ambiance ambiance, String repoName) {
    if (gitConnectorRefValue == null) {
      log.warn("GitConnectorRefValue is empty");
    }
    return connectorUtils.getConnectorDetailsWithToken(ngAccess, gitConnectorRefValue, true, ambiance, repoName);
  }

  public static String transformToSshUrl(String gitUrl) {
    if (gitUrl.startsWith("https://")) {
      String repoPath = gitUrl.substring(8);
      int indexOfSlash = repoPath.indexOf("/");
      String domainWithUser = repoPath.substring(0, indexOfSlash);
      String repoName = repoPath.substring(indexOfSlash + 1);
      String sshUrl = "git@" + domainWithUser + ":" + repoName + ".git";
      return sshUrl;
    } else {
      return gitUrl; // Already in SSH format or unsupported
    }
  }
  public static String getCompleteURLFromConnector(ConnectorDetails connectorDetails, String repoName) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    GitConnectionType gitConnectionType = getGitConnectionType(connectorDetails);
    String completeURL = scmConnector.getUrl();

    if (scmConnector.getConnectorType() == HARNESS) {
      completeURL = CiCodebaseUtils.getCompleteHarnessUrl(
          completeURL, connectorDetails.getOrgIdentifier(), connectorDetails.getProjectIdentifier(), repoName);
    } else if (isNotEmpty(repoName) && gitConnectionType == GitConnectionType.PROJECT) {
      if (scmConnector.getConnectorType() == AZURE_REPO) {
        completeURL = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(completeURL, repoName);
      }
    } else if (isNotEmpty(repoName) && (gitConnectionType == null || gitConnectionType == GitConnectionType.ACCOUNT)) {
      completeURL = StringUtils.join(StringUtils.stripEnd(scmConnector.getUrl(), PATH_SEPARATOR), PATH_SEPARATOR,
          StringUtils.stripStart(repoName, PATH_SEPARATOR));
    }

    return completeURL;
  }

  public static GitConnectionType getGitConnectionType(ConnectorDetails gitConnector) {
    if (gitConnector == null) {
      return null;
    }

    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return mapToGitConnectionType(gitConfigDTO.getConnectionType());
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrlType() == AwsCodeCommitUrlType.REPO ? GitConnectionType.REPO
                                                                    : GitConnectionType.ACCOUNT;
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getGitConnectionType();
    } else if (gitConnector.getConnectorType() == HARNESS) {
      HarnessConnectorDTO gitConfigDTO = (HarnessConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + gitConnector.getConnectorType());
    }
  }
}
