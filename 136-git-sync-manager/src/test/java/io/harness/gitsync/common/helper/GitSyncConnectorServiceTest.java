/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.UnexpectedException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.impl.GitSyncConnectorServiceImpl;
import io.harness.gitsync.common.service.GitSyncConnectorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.rule.Owner;
import io.harness.tasks.DecryptGitApiAccessHelper;

import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class GitSyncConnectorServiceTest extends GitSyncTestBase {
  @Mock YamlGitConfigService yamlGitConfigService;
  @Mock DecryptGitApiAccessHelper decryptGitApiAccessHelper;
  @Mock ConnectorService connectorService;
  private GitSyncConnectorService gitSyncConnectorService;
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final String ORG_IDENTIFIER = "org";
  private static final String PROJECT_IDENTIFIER = "project";
  private static final String CONNECTOR_REF = "connectorRef";
  GithubApiAccessDTO githubApiAccessDTO;
  GithubConnectorDTO githubConnectorDTO;
  private AutoCloseable mocks;

  @Before
  public void setup() {
    mocks = MockitoAnnotations.openMocks(this);
    gitSyncConnectorService = new GitSyncConnectorServiceImpl(
        connectorService, decryptGitApiAccessHelper, yamlGitConfigService, null, null, null);
    githubApiAccessDTO =
        GithubApiAccessDTO.builder()
            .type(GithubApiAccessType.OAUTH)
            .spec(GithubOauthDTO.builder()
                      .tokenRef(SecretRefData.builder().identifier("tokenRef").scope(Scope.PROJECT).build())
                      .build())
            .build();
    githubConnectorDTO = GithubConnectorDTO.builder().build();
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetScmConnector() {
    when(connectorService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .thenReturn(Optional.of(
            ConnectorResponseDTO.builder()
                .connector(ConnectorInfoDTO.builder().connectorConfig(GithubConnectorDTO.builder().build()).build())
                .build()));
    when(decryptGitApiAccessHelper.decryptScmApiAccess(any(), any(), any(), any()))
        .thenReturn(GithubConnectorDTO.builder().build());
    when(yamlGitConfigService.getByProjectIdAndRepoOptional(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "Repo"))
        .thenReturn(Optional.empty());
    ScmConnector scmConnector = gitSyncConnectorService.getDecryptedConnector(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF, "Repo");
    assertThat(scmConnector.getConnectorType()).isEqualTo(ConnectorType.GITHUB);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetScmConnectorByRef() {
    when(connectorService.getByRef(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .thenReturn(Optional.of(
            ConnectorResponseDTO.builder()
                .connector(ConnectorInfoDTO.builder().connectorConfig(GithubConnectorDTO.builder().build()).build())
                .build()));
    ScmConnector scmConnector =
        gitSyncConnectorService.getScmConnector(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF);
    assertThat(scmConnector.getConnectorType()).isEqualTo(ConnectorType.GITHUB);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetScmConnectorByRefWhenConnectorNotFound() {
    when(connectorService.getByRef(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .thenReturn(Optional.empty());
    assertThatThrownBy(()
                           -> gitSyncConnectorService.getScmConnector(
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .isInstanceOf(ConnectorNotFoundException.class);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetScmConnectorByRefWhenConnectorNotScmConnector() {
    when(connectorService.getByRef(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .thenReturn(Optional.of(
            ConnectorResponseDTO.builder()
                .connector(
                    ConnectorInfoDTO.builder().connectorConfig(AppDynamicsConnectorDTO.builder().build()).build())
                .build()));
    assertThatThrownBy(()
                           -> gitSyncConnectorService.getScmConnector(
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .isInstanceOf(UnexpectedException.class);
  }
}
