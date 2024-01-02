/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.helpers;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.services.ArtifactService;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class CdInstanceSummaryServiceHelperTest extends SSCAManagerTestBase {
  @InjectMocks CdInstanceSummaryServiceHelper cdInstanceSummaryServiceHelper;
  @Mock ArtifactService artifactService;
  private final BuilderFactory builderFactory = BuilderFactory.getDefault();
  private static String ACCOUNT_ID;
  private static String ORG_ID;
  private static String PROJECT_ID;

  @Before
  public void setup() {
    ACCOUNT_ID = builderFactory.getContext().getAccountId();
    ORG_ID = builderFactory.getContext().getOrgIdentifier();
    PROJECT_ID = builderFactory.getContext().getProjectIdentifier();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testIsK8sInstanceInfo() {
    assertThat(cdInstanceSummaryServiceHelper.isK8sInstanceInfo(builderFactory.getK8sInstanceDTOBuilder().build()))
        .isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFindCorrelatedArtifactsForK8sInstance() {
    ArtifactEntity image1Tag1Artifact = builderFactory.getArtifactEntityBuilder()
                                            .tag("tag1")
                                            .name("image1")
                                            .artifactCorrelationId("image1:tag1")
                                            .build();
    when(artifactService.getArtifactByCorrelationId(ACCOUNT_ID, ORG_ID, PROJECT_ID, "image1:tag1"))
        .thenReturn(image1Tag1Artifact);

    ArtifactEntity image3Tag3Artifact = builderFactory.getArtifactEntityBuilder()
                                            .tag("tag3")
                                            .name("image3")
                                            .artifactCorrelationId("image3:tag3")
                                            .build();
    when(artifactService.getLatestArtifactByImageNameAndTag(ACCOUNT_ID, ORG_ID, PROJECT_ID, "image3", "tag3"))
        .thenReturn(image3Tag3Artifact);

    Set<ArtifactEntity> artifactEntities = cdInstanceSummaryServiceHelper.findCorrelatedArtifactsForK8sInstance(
        builderFactory.getK8sInstanceDTOBuilder().build());
    assertThat(artifactEntities).isNotNull();
    assertThat(artifactEntities.size()).isEqualTo(2);
    assertThat(artifactEntities).isEqualTo(Set.of(image1Tag1Artifact, image3Tag3Artifact));
  }
}
