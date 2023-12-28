/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_MACHINE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CiCodebaseUtilsTest extends CategoryTest {
  private CiCodebaseUtils ciCodebaseUtils = new CiCodebaseUtils();
  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetGitEnvVariables() {
    String repoName = "myRepoName";
    String scmHostName = "gitlab.com";
    String scmUrl = "git@" + scmHostName + ":org";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITLAB)
            .connectorConfig(GitlabConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url(scmUrl)
                                 .authentication(GitlabAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();
    final Map<String, String> gitEnvVariables = ciCodebaseUtils.getGitEnvVariables(connectorDetails, repoName);
    assertThat(gitEnvVariables.get(DRONE_NETRC_MACHINE)).isEqualTo(scmHostName);
    assertThat(gitEnvVariables.get(DRONE_REMOTE_URL)).isEqualTo(scmUrl + "/" + repoName + ".git");
  }
}
