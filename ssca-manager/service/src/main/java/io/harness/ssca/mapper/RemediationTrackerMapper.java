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
import io.harness.spec.server.ssca.v1.model.CreateTicketRequest;
import io.harness.spec.server.ssca.v1.model.EnvironmentType;
import io.harness.spec.server.ssca.v1.model.PipelineInfo;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactDeploymentsListingResponse;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactDetailsResponse;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactListingResponse;
import io.harness.spec.server.ssca.v1.model.RemediationDetailsResponse;
import io.harness.spec.server.ssca.v1.model.RemediationListingResponse;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.ticket.TicketRequestDto;
import io.harness.ssca.entities.remediation_tracker.ArtifactInfo;
import io.harness.ssca.entities.remediation_tracker.CVEVulnerability;
import io.harness.ssca.entities.remediation_tracker.ContactInfo;
import io.harness.ssca.entities.remediation_tracker.DefaultVulnerability;
import io.harness.ssca.entities.remediation_tracker.DeploymentsCount;
import io.harness.ssca.entities.remediation_tracker.EnvironmentInfo;
import io.harness.ssca.entities.remediation_tracker.RemediationCondition;
import io.harness.ssca.entities.remediation_tracker.RemediationStatus;
import io.harness.ssca.entities.remediation_tracker.RemediationTrackerEntity;
import io.harness.ssca.entities.remediation_tracker.VulnerabilityInfo;
import io.harness.ssca.entities.remediation_tracker.VulnerabilityInfoType;
import io.harness.ssca.entities.remediation_tracker.VulnerabilitySeverity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
public class RemediationTrackerMapper {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final long DAY_TO_MILLI = 24 * 60 * 60 * 1000L;

  public RemediationCondition.Operator mapOperator(
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
        throw new InvalidRequestException("Invalid operator: " + condition.getOperator());
    }
  }

  public io.harness.spec.server.ssca.v1.model.RemediationCondition.OperatorEnum mapOperator(
      RemediationCondition.Operator operator) {
    switch (operator) {
      case LESS_THAN:
        return io.harness.spec.server.ssca.v1.model.RemediationCondition.OperatorEnum.LESSTHAN;
      case LESS_THAN_EQUALS:
        return io.harness.spec.server.ssca.v1.model.RemediationCondition.OperatorEnum.LESSTHANEQUALS;
      case EQUALS:
        return io.harness.spec.server.ssca.v1.model.RemediationCondition.OperatorEnum.MATCHES;
      case ALL:
        return io.harness.spec.server.ssca.v1.model.RemediationCondition.OperatorEnum.ALL;
      default:
        throw new InvalidRequestException("Invalid operator: " + operator);
    }
  }

  public TicketRequestDto mapToTicketRequestDto(String remediationId, CreateTicketRequest requestBody) {
    Map<String, List<String>> identifiersCopy = new HashMap<>();

    // If identifiers is not null, add it to identifiersCopy
    if (requestBody.getIdentifiers() != null) {
      identifiersCopy.putAll((Map<? extends String, ? extends List<String>>) requestBody.getIdentifiers());
    }

    // Add or update the remediationId in the identifiers map
    identifiersCopy.put("remediationId", List.of(remediationId));

    return TicketRequestDto.builder()
        .description(requestBody.getDescription())
        .exists(requestBody.isExists())
        .externalId(requestBody.getExternalId())
        .identifiers(identifiersCopy)
        .issueType(requestBody.getIssueType())
        .priority(requestBody.getPriority())
        .projectKey(requestBody.getProjectKey())
        .title(requestBody.getTitle())
        .build();
  }

  public ContactInfo mapContactInfo(io.harness.spec.server.ssca.v1.model.ContactInfo contactInfo) {
    return (contactInfo == null)
        ? null
        : ContactInfo.builder().email(contactInfo.getEmail()).name(contactInfo.getName()).build();
  }

  public io.harness.spec.server.ssca.v1.model.ContactInfo mapContactInfo(ContactInfo contactInfo) {
    return (contactInfo == null) ? null
                                 : new io.harness.spec.server.ssca.v1.model.ContactInfo()
                                       .email(contactInfo.getEmail())
                                       .name(contactInfo.getName());
  }

  public RemediationCondition mapRemediationCondition(
      io.harness.spec.server.ssca.v1.model.RemediationCondition remediationCondition) {
    return RemediationCondition.builder()
        .version(remediationCondition.getVersion())
        .operator(mapOperator(remediationCondition))
        .build();
  }

  public io.harness.spec.server.ssca.v1.model.RemediationCondition mapRemediationCondition(
      RemediationCondition remediationCondition) {
    return new io.harness.spec.server.ssca.v1.model.RemediationCondition()
        .version(remediationCondition.getVersion())
        .operator(mapOperator(remediationCondition.getOperator()));
  }

  public VulnerabilityInfo mapVulnerabilityInfo(
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
        throw new InvalidRequestException("Invalid Vulnerability type: " + vulnerabilityInfo.getType());
    }
  }

  public VulnerabilitySeverity mapSeverityToVulnerabilitySeverity(
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
        throw new InvalidRequestException("Invalid severity: " + severity);
    }
  }

  private io.harness.spec.server.ssca.v1.model.VulnerabilitySeverity mapSeverityToVulnerabilitySeverity(
      VulnerabilitySeverity severity) {
    switch (severity) {
      case INFO:
        return io.harness.spec.server.ssca.v1.model.VulnerabilitySeverity.INFO;
      case LOW:
        return io.harness.spec.server.ssca.v1.model.VulnerabilitySeverity.LOW;
      case MEDIUM:
        return io.harness.spec.server.ssca.v1.model.VulnerabilitySeverity.MEDIUM;
      case HIGH:
        return io.harness.spec.server.ssca.v1.model.VulnerabilitySeverity.HIGH;
      case CRITICAL:
        return io.harness.spec.server.ssca.v1.model.VulnerabilitySeverity.CRITICAL;
      default:
        throw new InvalidRequestException("Invalid severity: " + severity);
    }
  }

  private io.harness.spec.server.ssca.v1.model.RemediationStatus mapStatus(RemediationStatus status) {
    switch (status) {
      case ON_GOING:
        return io.harness.spec.server.ssca.v1.model.RemediationStatus.ON_GOING;
      case COMPLETED:
        return io.harness.spec.server.ssca.v1.model.RemediationStatus.COMPLETED;
      default:
        throw new InvalidRequestException("Invalid status: " + status);
    }
  }

  private io.harness.spec.server.ssca.v1.model.DeploymentsCount mapDeploymentsCount(DeploymentsCount deploymentsCount) {
    return new io.harness.spec.server.ssca.v1.model.DeploymentsCount()
        .pendingProdCount(deploymentsCount.getPendingProdCount())
        .pendingNonProdCount(deploymentsCount.getPendingNonProdCount())
        .patchedProdCount(deploymentsCount.getPatchedProdCount())
        .patchedNonProdCount(deploymentsCount.getPatchedNonProdCount());
  }

  public io.harness.spec.server.ssca.v1.model.EnvironmentType mapEnvType(EnvType environmentType) {
    switch (environmentType) {
      case PreProduction:
        return EnvironmentType.PROD;
      case Production:
        return EnvironmentType.PREPROD;
      default:
        throw new InvalidRequestException("Invalid environment type: " + environmentType);
    }
  }

  public EnvType mapEnvType(String environmentType) {
    if (environmentType == null) {
      return null;
    }
    switch (environmentType) {
      case "Prod":
        return EnvType.Production;
      case "PreProd":
        return EnvType.PreProduction;
      default:
        throw new InvalidRequestException("Invalid environment type: " + environmentType);
    }
  }

  public RemediationListingResponse mapRemediationListResponse(RemediationTrackerEntity remediationTrackerEntity) {
    return new RemediationListingResponse()
        .id(remediationTrackerEntity.getUuid())
        .component(remediationTrackerEntity.getVulnerabilityInfo().getComponent())
        .cve(getCveFromVulnerabilityInfo(remediationTrackerEntity.getVulnerabilityInfo()))
        .remediationCondition(mapRemediationCondition(remediationTrackerEntity.getCondition()))
        .severity(mapSeverityToVulnerabilitySeverity(remediationTrackerEntity.getVulnerabilityInfo().getSeverity()))
        .status(mapStatus(remediationTrackerEntity.getStatus()))
        .contact(mapContactInfo(remediationTrackerEntity.getContactInfo()))
        .targetDate(formatTargetDate(remediationTrackerEntity.getTargetEndDateEpochDay()))
        .cve(getCveFromVulnerabilityInfo(remediationTrackerEntity.getVulnerabilityInfo()))
        .artifacts(countNonExcludedArtifacts(remediationTrackerEntity))
        .deploymentsCount(mapDeploymentsCount(remediationTrackerEntity.getDeploymentsCount()))
        .scheduleStatus(calculateScheduleStatus(remediationTrackerEntity));
  }

  public RemediationDetailsResponse mapRemediationDetailsResponse(RemediationTrackerEntity remediationTrackerEntity) {
    return new RemediationDetailsResponse()
        .id(remediationTrackerEntity.getUuid())
        .component(remediationTrackerEntity.getVulnerabilityInfo().getComponent())
        .cve(getCveFromVulnerabilityInfo(remediationTrackerEntity.getVulnerabilityInfo()))
        .severity(mapSeverityToVulnerabilitySeverity(remediationTrackerEntity.getVulnerabilityInfo().getSeverity()))
        .status(mapStatus(remediationTrackerEntity.getStatus()))
        .contact(mapContactInfo(remediationTrackerEntity.getContactInfo()))
        .artifacts(countNonExcludedArtifacts(remediationTrackerEntity))
        .artifactsExcluded(countExcludedArtifacts(remediationTrackerEntity))
        .deploymentsCount(mapDeploymentsCount(remediationTrackerEntity.getDeploymentsCount()))
        .comments(remediationTrackerEntity.getComments())
        .endTimeMilli(remediationTrackerEntity.getEndTimeMilli())
        .startTimeMilli(remediationTrackerEntity.getStartTimeMilli())
        .environments(countEnvironments(remediationTrackerEntity))
        .remediationCondition(mapRemediationCondition(remediationTrackerEntity.getCondition()))
        .vulnerabilityDescription(remediationTrackerEntity.getVulnerabilityInfo().getVulnerabilityDescription());
  }

  public RemediationArtifactDetailsResponse mapArtifactInfoToArtifactDetailsResponse(
      RemediationTrackerEntity remediationTracker, ArtifactInfo artifactInfo) {
    return new RemediationArtifactDetailsResponse()
        .id(artifactInfo.getArtifactId())
        .remediationId(remediationTracker.getUuid())
        .component(remediationTracker.getVulnerabilityInfo().getComponent())
        .cve(getCveFromVulnerabilityInfo(remediationTracker.getVulnerabilityInfo()))
        .severity(mapSeverityToVulnerabilitySeverity(remediationTracker.getVulnerabilityInfo().getSeverity()))
        .status(mapStatus(remediationTracker.getStatus()))
        .contact(mapContactInfo(remediationTracker.getContactInfo()))
        .deploymentsCount(mapDeploymentsCount(artifactInfo.getDeploymentsCount()))
        .artifactName(artifactInfo.getArtifactName())
        .latestFixedArtifact(artifactInfo.getLatestTagWithFix());
  }

  public RemediationArtifactDeploymentsListingResponse mapEnvironmentInfoToArtifactDeploymentsListingResponse(
      EnvironmentInfo environmentInfo) {
    return new RemediationArtifactDeploymentsListingResponse()
        .identifier(environmentInfo.getEnvIdentifier())
        .name(environmentInfo.getEnvName())
        .type(mapEnvType(environmentInfo.getEnvType()))
        .tag(environmentInfo.getTag())
        .status(environmentInfo.isPatched() ? io.harness.spec.server.ssca.v1.model.RemediationStatus.COMPLETED
                                            : io.harness.spec.server.ssca.v1.model.RemediationStatus.ON_GOING)
        .deploymentPipeline(mapDeploymentPipeline(environmentInfo.getDeploymentPipeline()));
  }

  public RemediationArtifactListingResponse mapArtifactInfoToArtifactListingResponse(ArtifactInfo artifactInfo) {
    return new RemediationArtifactListingResponse()
        .id(artifactInfo.getArtifactId())
        .name(artifactInfo.getArtifactName())
        .isExcluded(artifactInfo.isExcluded())
        .deployments(mapDeploymentsCount(artifactInfo.getDeploymentsCount()));
  }

  private PipelineInfo mapDeploymentPipeline(io.harness.ssca.entities.remediation_tracker.Pipeline deploymentPipeline) {
    return (deploymentPipeline == null) ? null
                                        : new PipelineInfo()
                                              .id(deploymentPipeline.getPipelineId())
                                              .name(deploymentPipeline.getPipelineName())
                                              .triggeredBy(deploymentPipeline.getTriggeredBy())
                                              .triggeredAt(deploymentPipeline.getTriggeredAt())
                                              .triggeredById(deploymentPipeline.getTriggeredById())
                                              .executionId(deploymentPipeline.getPipelineExecutionId());
  }
  private String getCveFromVulnerabilityInfo(VulnerabilityInfo vulnerabilityInfo) {
    return (vulnerabilityInfo.getType().equals(VulnerabilityInfoType.CVE))
        ? ((CVEVulnerability) vulnerabilityInfo).getCve()
        : null;
  }

  private String formatTargetDate(Long targetEndDateEpochDay) {
    return (targetEndDateEpochDay != null) ? LocalDate.ofEpochDay(targetEndDateEpochDay).format(DATE_FORMATTER) : null;
  }

  private long countNonExcludedArtifacts(RemediationTrackerEntity remediationTrackerEntity) {
    return remediationTrackerEntity.getArtifactInfos()
        .values()
        .stream()
        .filter(artifactInfo -> !artifactInfo.isExcluded())
        .count();
  }

  private long countExcludedArtifacts(RemediationTrackerEntity remediationTrackerEntity) {
    return remediationTrackerEntity.getArtifactInfos().values().stream().filter(ArtifactInfo::isExcluded).count();
  }

  private long countEnvironments(RemediationTrackerEntity remediationTrackerEntity) {
    return remediationTrackerEntity.getArtifactInfos()
        .values()
        .stream()
        .filter(artifactInfo -> !artifactInfo.isExcluded())
        .map(artifactInfo -> artifactInfo.getEnvironments().size())
        .count();
  }

  private String calculateScheduleStatus(RemediationTrackerEntity remediationTrackerEntity) {
    if (remediationTrackerEntity.getTargetEndDateEpochDay() == null) {
      return "No target date";
    }

    long currentOrEndTimeMilli = System.currentTimeMillis();

    if (remediationTrackerEntity.getStatus() == RemediationStatus.COMPLETED
        && remediationTrackerEntity.getEndTimeMilli() != null) {
      currentOrEndTimeMilli = remediationTrackerEntity.getEndTimeMilli();
    }

    if (currentOrEndTimeMilli > remediationTrackerEntity.getTargetEndDateEpochDay() * DAY_TO_MILLI + DAY_TO_MILLI) {
      return "Delayed by "
          + (currentOrEndTimeMilli - remediationTrackerEntity.getTargetEndDateEpochDay() * DAY_TO_MILLI) / DAY_TO_MILLI
          + " days";
    }

    if (remediationTrackerEntity.getStatus() == RemediationStatus.COMPLETED) {
      return "On time";
    } else {
      return "Open since " + formatTimeDuration(currentOrEndTimeMilli - remediationTrackerEntity.getStartTimeMilli());
    }
  }

  private String formatTimeDuration(long milliSeconds) {
    long seconds = milliSeconds / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;
    long days = hours / 24;

    if (days > 0) {
      return days + " days";
    } else if (hours > 0) {
      return hours + " hours";
    } else if (minutes > 0) {
      return minutes + " minutes";
    } else {
      return seconds + " seconds";
    }
  }
}