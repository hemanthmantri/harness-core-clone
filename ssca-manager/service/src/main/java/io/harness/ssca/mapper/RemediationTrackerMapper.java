/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ssca.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ssca.entities.remediation_tracker.CVEVulnerability;
import io.harness.ssca.entities.remediation_tracker.ContactInfo;
import io.harness.ssca.entities.remediation_tracker.DefaultVulnerability;
import io.harness.ssca.entities.remediation_tracker.RemediationCondition;
import io.harness.ssca.entities.remediation_tracker.VulnerabilityInfo;
import io.harness.ssca.entities.remediation_tracker.VulnerabilityInfoType;
import io.harness.ssca.entities.remediation_tracker.VulnerabilitySeverity;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
public class RemediationTrackerMapper {
  public RemediationCondition.Operator mapOperatorToRemediationConditionOperator(
      io.harness.spec.server.ssca.v1.model.RemediationCondition condition) {
    switch (condition.getOperator()) {
      case LESSTHAN:
        return RemediationCondition.Operator.LESS_THAN;
      case LESSTHANEQUALS:
        return RemediationCondition.Operator.LESS_THAN_EQUALS;
      case MATCHES:
        return RemediationCondition.Operator.EQUALS;
      case ALL:
        return RemediationCondition.Operator.ALL;
      default:
        throw new InvalidRequestException("Operator could only be one of LessThan / LessThanEquals / Matches / All");
    }
  }
  public ContactInfo getContactInfo(io.harness.spec.server.ssca.v1.model.ContactInfo contactInfo) {
    if (contactInfo == null) {
      return null;
    }
    return ContactInfo.builder().email(contactInfo.getEmail()).name(contactInfo.getName()).build();
  }

  public RemediationCondition getRemediationCondition(
      io.harness.spec.server.ssca.v1.model.RemediationCondition remediationCondition) {
    return RemediationCondition.builder()
        .version(remediationCondition.getVersion())
        .operator(mapOperatorToRemediationConditionOperator(remediationCondition))
        .build();
  }

  public VulnerabilityInfo getVulnerabilityInfo(
      io.harness.spec.server.ssca.v1.model.VulnerabilityInfo vulnerabilityInfo) {
    switch (vulnerabilityInfo.getType()) {
      case "Default":
        return DefaultVulnerability.builder()
            .component(vulnerabilityInfo.getComponentName())
            .vulnerabilityDescription(vulnerabilityInfo.getVulnerabilityDescription())
            .type(VulnerabilityInfoType.DEFAULT)
            .severity(mapSeverityToVulnerabilitySeverity(vulnerabilityInfo.getSeverity()))
            .build();
      case "CVE":
        if (!(vulnerabilityInfo instanceof io.harness.spec.server.ssca.v1.model.CVEVulnerability)) {
          throw new InvalidRequestException(
              "Vulnerability Info Type is CVE, but the object is not of class CVEVulnerability");
        }
        return CVEVulnerability.builder()
            .cve(((io.harness.spec.server.ssca.v1.model.CVEVulnerability) vulnerabilityInfo).getCve())
            .component(vulnerabilityInfo.getComponentName())
            .vulnerabilityDescription(vulnerabilityInfo.getVulnerabilityDescription())
            .type(VulnerabilityInfoType.CVE)
            .severity(mapSeverityToVulnerabilitySeverity(vulnerabilityInfo.getSeverity()))
            .build();
      default:
        throw new InvalidRequestException("Vulnerability can only be of type Default / CVE");
    }
  }

  private VulnerabilitySeverity mapSeverityToVulnerabilitySeverity(
      io.harness.spec.server.ssca.v1.model.VulnerabilitySeverity severity) {
    switch (severity) {
      case INFO:
        return VulnerabilitySeverity.INFO;
      case LOW:
        return VulnerabilitySeverity.LOW;
      case MEDIUM:
        return VulnerabilitySeverity.MEDIUM;
      case HIGH:
        return VulnerabilitySeverity.HIGH;
      case CRITICAL:
        return VulnerabilitySeverity.CRITICAL;
      default:
        throw new InvalidRequestException("Severity could only be one of NONE / LOW / MEDIUM / HIGH");
    }
  }
}
