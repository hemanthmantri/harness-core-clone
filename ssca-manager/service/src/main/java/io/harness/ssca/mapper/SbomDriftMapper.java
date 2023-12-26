/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.spec.server.ssca.v1.model.ComponentDrift;
import io.harness.spec.server.ssca.v1.model.ComponentDriftSummary;
import io.harness.spec.server.ssca.v1.model.ComponentSummary;
import io.harness.spec.server.ssca.v1.model.LicenseDrift;
import io.harness.spec.server.ssca.v1.model.LicenseDriftSummary;
import io.harness.spec.server.ssca.v1.model.OrchestrationDriftSummary;
import io.harness.spec.server.ssca.v1.model.OrchestrationStepDriftRequestBody;
import io.harness.ssca.beans.drift.ComponentDriftStatus;
import io.harness.ssca.beans.drift.DriftBase;
import io.harness.ssca.beans.drift.LicenseDriftStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
public class SbomDriftMapper {
  public ComponentDriftStatus mapStatusToComponentDriftStatus(String status) {
    switch (status) {
      case "added":
        return ComponentDriftStatus.ADDED;
      case "modified":
        return ComponentDriftStatus.MODIFIED;
      case "deleted":
        return ComponentDriftStatus.DELETED;
      case "all":
        return null;
      default:
        throw new InvalidRequestException("status could only be one of all / added / modified / deleted");
    }
  }

  public LicenseDriftStatus mapStatusToLicenseDriftStatus(String status) {
    switch (status) {
      case "added":
        return LicenseDriftStatus.ADDED;
      case "deleted":
        return LicenseDriftStatus.DELETED;
      case "all":
        return null;
      default:
        throw new InvalidRequestException("status could only be one of all / added / deleted");
    }
  }

  public DriftBase getDriftBase(OrchestrationStepDriftRequestBody body) {
    switch (body.getBase()) {
      case "baseline":
        return DriftBase.BASELINE;
      case "last_generated_sbom":
        return DriftBase.LAST_GENERATED_SBOM;
      default:
        throw new InvalidRequestException("Base could only be baseline / last_generated_sbom");
    }
  }

  public List<ComponentDrift> toComponentDriftResponseList(
      List<io.harness.ssca.beans.drift.ComponentDrift> componentDrifts) {
    if (EmptyPredicate.isEmpty(componentDrifts)) {
      return new ArrayList<>();
    }
    List<ComponentDrift> componentDriftList = new ArrayList<>();
    for (io.harness.ssca.beans.drift.ComponentDrift componentDrift : componentDrifts) {
      if (componentDrift == null || componentDrift.getStatus() == null) {
        continue;
      }
      componentDriftList.add(new ComponentDrift()
                                 .newComponent(toComponentSummaryResponse(componentDrift.getNewComponent()))
                                 .oldComponent(toComponentSummaryResponse(componentDrift.getOldComponent()))
                                 .status(componentDrift.getStatus().toString()));
    }
    return componentDriftList;
  }

  public List<LicenseDrift> toLicenseDriftResponseList(List<io.harness.ssca.beans.drift.LicenseDrift> licenseDrifts) {
    if (EmptyPredicate.isEmpty(licenseDrifts)) {
      return new ArrayList<>();
    }
    List<LicenseDrift> licenseDriftList = new ArrayList<>();
    for (io.harness.ssca.beans.drift.LicenseDrift licenseDrift : licenseDrifts) {
      if (licenseDrift == null || licenseDrift.getStatus() == null) {
        continue;
      }
      licenseDriftList.add(new LicenseDrift()
                               .license(licenseDrift.getName())
                               .components(licenseDrift.getComponents()
                                               .stream()
                                               .map(SbomDriftMapper::toComponentSummaryResponse)
                                               .collect(Collectors.toList()))
                               .status(licenseDrift.getStatus().toString()));
    }
    return licenseDriftList;
  }

  private ComponentSummary toComponentSummaryResponse(io.harness.ssca.beans.drift.ComponentSummary componentSummary) {
    if (componentSummary == null) {
      return null;
    }
    return new ComponentSummary()
        .packageName(componentSummary.getPackageName())
        .packageVersion(componentSummary.getPackageVersion())
        .packageLicense(componentSummary.getPackageLicense() != null
                ? String.join(", ", componentSummary.getPackageLicense())
                : null)
        .purl(componentSummary.getPurl())
        .packageSupplier(componentSummary.getPackageOriginatorName())
        .packageManager(componentSummary.getPackageManager());
  }

  public ComponentDriftSummary getComponentDriftSummary(OrchestrationDriftSummary orchestrationDriftSummary) {
    return new ComponentDriftSummary()
        .total(orchestrationDriftSummary.getComponentDrifts())
        .added(orchestrationDriftSummary.getComponentsAdded())
        .deleted(orchestrationDriftSummary.getComponentsDeleted())
        .modified(orchestrationDriftSummary.getComponentsModified());
  }

  public LicenseDriftSummary getLicenseDriftSummary(OrchestrationDriftSummary orchestrationDriftSummary) {
    return new LicenseDriftSummary()
        .total(orchestrationDriftSummary.getLicenseDrifts())
        .added(orchestrationDriftSummary.getLicenseAdded())
        .deleted(orchestrationDriftSummary.getLicenseDeleted());
  }
}
