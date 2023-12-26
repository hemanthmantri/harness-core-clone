/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ScopeInfoServiceImpl implements ScopeInfoService {
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final ScopeInfoHelper scopeInfoHelper;

  @Inject
  public ScopeInfoServiceImpl(
      OrganizationService organizationService, ProjectService projectService, ScopeInfoHelper scopeInfoHelper) {
    this.organizationService = organizationService;
    this.projectService = projectService;
    this.scopeInfoHelper = scopeInfoHelper;
  }

  @Override
  public Optional<ScopeInfo> getScopeInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(orgIdentifier) && isEmpty(projectIdentifier)) {
      return Optional.of(
          scopeInfoHelper.populateScopeInfo(ScopeLevel.ACCOUNT, accountIdentifier, accountIdentifier, null, null));
    }

    if (isEmpty(projectIdentifier)) {
      return organizationService.getScopeInfo(accountIdentifier, orgIdentifier);
    }

    return projectService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
