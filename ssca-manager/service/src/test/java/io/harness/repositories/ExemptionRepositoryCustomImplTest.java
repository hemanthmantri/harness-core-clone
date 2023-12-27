/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.repositories.exemption.ExemptionRepositoryCustomImpl;
import io.harness.rule.Owner;
import io.harness.ssca.entities.OperatorEntity;
import io.harness.ssca.entities.exemption.Exemption;
import io.harness.ssca.entities.exemption.Exemption.ExemptionDuration;
import io.harness.ssca.entities.exemption.Exemption.ExemptionInitiator;
import io.harness.ssca.entities.exemption.Exemption.ExemptionKeys;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class ExemptionRepositoryCustomImplTest extends SSCAManagerTestBase {
  @Inject private ExemptionRepositoryCustomImpl exemptionRepositoryCustom;
  @Mock MongoTemplate mongoTemplate;
  private BuilderFactory builderFactory;
  private List<String> artifactIds;
  private List<String> componentNames;

  @Before
  public void setup() throws IllegalAccessException {
    FieldUtils.writeField(exemptionRepositoryCustom, "mongoTemplate", mongoTemplate, true);
    builderFactory = BuilderFactory.getDefault();
    artifactIds = new ArrayList<>();
    int NUMBER_OF_ARTIFACT_IDS = 5;
    for (int i = 0; i < NUMBER_OF_ARTIFACT_IDS; ++i) {
      artifactIds.add(generateUuid());
    }
    componentNames = new ArrayList<>();
    int NUMBER_OF_COMPONENT_NAMES = 5;
    for (int i = 0; i < NUMBER_OF_COMPONENT_NAMES; ++i) {
      componentNames.add(generateUuid());
    }
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void findExemptions_withPagination() {
    List exemptions = getExemptions();
    Mockito.when(mongoTemplate.find(any(), any())).thenReturn(exemptions);
    Mockito.when(mongoTemplate.count(any(), (Class<?>) any())).thenReturn(30l);
    Criteria criteria = getCriteria();
    Pageable pageable = PageRequest.of(1, 8);
    Page<Exemption> page = exemptionRepositoryCustom.findExemptions(criteria, pageable);
    ArgumentCaptor<Query> argumentCaptor = ArgumentCaptor.forClass(Query.class);
    assertThat(page.getTotalPages()).isEqualTo(4);
    assertThat(page.getTotalElements()).isEqualTo(30);
    assertThat(page.getContent()).hasSize(30);
    verify(mongoTemplate, times(1)).find(argumentCaptor.capture(), any());
    Document sortDocument = argumentCaptor.getValue().getSortObject();
    assertThat(sortDocument).containsEntry("createdAt", 1);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void findExemptions() {
    List exemptions = getExemptions();
    Mockito.when(mongoTemplate.find(any(), any())).thenReturn(exemptions);
    Criteria criteria = getCriteria();
    List<Exemption> fetchedExemptions = exemptionRepositoryCustom.findExemptions(criteria);
    ArgumentCaptor<Query> argumentCaptor = ArgumentCaptor.forClass(Query.class);
    assertThat(fetchedExemptions).hasSize(exemptions.size());
    verify(mongoTemplate, times(1)).find(argumentCaptor.capture(), any());
    Document sortDocument = argumentCaptor.getValue().getSortObject();
    assertThat(sortDocument).containsEntry("createdAt", 1);
  }

  private Criteria getCriteria() {
    return Criteria.where(ExemptionKeys.accountId)
        .is(builderFactory.getContext().getAccountId())
        .and(ExemptionKeys.orgIdentifier)
        .is(builderFactory.getContext().getOrgIdentifier())
        .and(ExemptionKeys.projectIdentifier)
        .is(builderFactory.getContext().getProjectIdentifier())
        .and(ExemptionKeys.artifactId)
        .is(artifactIds.get(0))
        .and(ExemptionKeys.componentName)
        .is(componentNames.get(0));
  }

  private List<Exemption> getExemptions() {
    List<Exemption> exemptions = new ArrayList<>();
    for (String artifactId : artifactIds) {
      for (String componentName : componentNames) {
        Exemption exemption = getExemption(artifactId, componentName);
        exemptions.add(exemption);
      }
    }
    for (String componentName : componentNames) {
      Exemption exemption = getExemption(null, componentName);
      exemptions.add(exemption);
    }
    return exemptions;
  }

  private Exemption getExemption(String artifactId, String componentName) {
    return Exemption.builder()
        .accountId(builderFactory.getContext().getAccountId())
        .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
        .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
        .artifactId(artifactId)
        .componentName(componentName)
        .componentVersion("componentVersion")
        .versionOperator(OperatorEntity.LESSTHAN)
        .exemptionDuration(ExemptionDuration.builder().alwaysExempt(true).build())
        .exemptionInitiator(ExemptionInitiator.builder()
                                .projectId(builderFactory.getContext().getProjectIdentifier())
                                .artifactId(artifactId)
                                .enforcementId("enforcementId")
                                .build())
        .build();
  }
}
