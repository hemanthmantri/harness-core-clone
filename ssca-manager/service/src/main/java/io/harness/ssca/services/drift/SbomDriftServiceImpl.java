/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services.drift;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.drift.SbomDriftRepository;
import io.harness.spec.server.ssca.v1.model.ArtifactSbomDriftRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactSbomDriftResponse;
import io.harness.spec.server.ssca.v1.model.OrchestrationDriftSummary;
import io.harness.ssca.beans.BaselineDTO;
import io.harness.ssca.beans.drift.ComponentDrift;
import io.harness.ssca.beans.drift.ComponentDriftResults;
import io.harness.ssca.beans.drift.ComponentDriftStatus;
import io.harness.ssca.beans.drift.ComponentSummary;
import io.harness.ssca.beans.drift.DriftBase;
import io.harness.ssca.beans.drift.LicenseDrift;
import io.harness.ssca.beans.drift.LicenseDriftResults;
import io.harness.ssca.beans.drift.LicenseDriftStatus;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.entities.drift.DriftEntity;
import io.harness.ssca.entities.drift.DriftEntity.DriftEntityKeys;
import io.harness.ssca.helpers.SbomDriftCalculator;
import io.harness.ssca.mapper.SbomDriftMapper;
import io.harness.ssca.services.ArtifactService;
import io.harness.ssca.services.BaselineService;
import io.harness.ssca.services.NormalisedSbomComponentService;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.SSCA)
public class SbomDriftServiceImpl implements SbomDriftService {
  @Inject SbomDriftRepository sbomDriftRepository;
  @Inject ArtifactService artifactService;
  @Inject SbomDriftCalculator sbomDriftCalculator;
  @Inject NormalisedSbomComponentService normalisedSbomComponentService;
  @Inject BaselineService baselineService;

  @Override
  public ArtifactSbomDriftResponse calculateSbomDrift(
      String accountId, String orgId, String projectId, String artifactId, ArtifactSbomDriftRequestBody requestBody) {
    String tag = requestBody.getTag();
    String baseTag = requestBody.getBaseTag();
    ArtifactEntity baseArtifact = artifactService.getLatestArtifact(accountId, orgId, projectId, artifactId, baseTag);
    if (baseArtifact == null) {
      throw new InvalidRequestException("Could not find artifact with tag: " + baseTag);
    }

    ArtifactEntity driftArtifact = artifactService.getLatestArtifact(accountId, orgId, projectId, artifactId, tag);
    if (driftArtifact == null) {
      throw new InvalidRequestException("Could not find artifact with tag: " + tag);
    }

    return calculateAndStoreSbomDrift(driftArtifact, baseArtifact, DriftBase.MANUAL);
  }

  private ArtifactSbomDriftResponse calculateAndStoreSbomDrift(
      ArtifactEntity driftArtifact, ArtifactEntity baseArtifact, DriftBase base) {
    validateSbomToolAndFormat(driftArtifact, baseArtifact);
    String accountId = driftArtifact.getAccountId();
    String orgId = driftArtifact.getOrgId();
    String projectId = driftArtifact.getProjectId();
    Optional<DriftEntity> optionalDriftEntity =
        sbomDriftRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndOrchestrationIdAndBaseOrchestrationId(
                accountId, orgId, projectId, driftArtifact.getOrchestrationId(), baseArtifact.getOrchestrationId());
    DriftEntity driftEntity;
    if (optionalDriftEntity.isPresent()) {
      driftEntity = optionalDriftEntity.get();
      // only update valid until if drift entity was saved for manual run.
      if (base == DriftBase.MANUAL && driftEntity.getBase() == DriftBase.MANUAL) {
        // update validUntil if same request has been found.
        Criteria criteria = Criteria.where(DriftEntityKeys.uuid).is(driftEntity.getUuid());
        Update update = new Update();
        update.set(DriftEntityKeys.validUntil, Date.from(OffsetDateTime.now().plusHours(1).toInstant()));
        sbomDriftRepository.update(new Query(criteria), update);
      }
    } else {
      List<ComponentDrift> componentDrifts = sbomDriftCalculator.findComponentDrifts(
          baseArtifact.getOrchestrationId(), driftArtifact.getOrchestrationId());
      List<LicenseDrift> licenseDrifts =
          sbomDriftCalculator.findLicenseDrift(baseArtifact.getOrchestrationId(), driftArtifact.getOrchestrationId());
      driftEntity = sbomDriftRepository.save(
          DriftEntity.builder()
              .accountIdentifier(accountId)
              .orgIdentifier(orgId)
              .projectIdentifier(projectId)
              .artifactId(driftArtifact.getArtifactId())
              .tag(driftArtifact.getTag())
              .baseTag(baseArtifact.getTag())
              .base(base)
              .orchestrationId(driftArtifact.getOrchestrationId())
              .baseOrchestrationId(baseArtifact.getOrchestrationId())
              .componentDrifts(componentDrifts)
              .licenseDrifts(licenseDrifts)
              .validUntil(base == DriftBase.MANUAL ? Date.from(OffsetDateTime.now().plusHours(1).toInstant())
                                                   : Date.from(OffsetDateTime.now().plusMonths(6).toInstant()))
              .build());
    }
    OrchestrationDriftSummary summary = buildDriftSummaryFromEntity(driftEntity);
    return new ArtifactSbomDriftResponse()
        .driftId(driftEntity.getUuid())
        .tag(driftArtifact.getTag())
        .baseTag(baseArtifact.getTag())
        .artifactName(driftArtifact.getName())
        .totalDrifts(summary.getTotalDrifts())
        .componentDriftSummary(SbomDriftMapper.getComponentDriftSummary(summary))
        .licenseDriftSummary(SbomDriftMapper.getLicenseDriftSummary(summary));
  }

  void validateSbomToolAndFormat(ArtifactEntity driftArtifact, ArtifactEntity baseArtifact) {
    if (driftArtifact.getSbom() != null && baseArtifact.getSbom() != null) {
      if (EmptyPredicate.isNotEmpty(driftArtifact.getSbom().getTool())
          && !driftArtifact.getSbom().getTool().equals(baseArtifact.getSbom().getTool())) {
        throw new InvalidRequestException(String.format(
            "Not proceeding with drift because sbom tool %s for base tag %s does not match with sbom tool %s of tag %s",
            baseArtifact.getSbom().getTool(), baseArtifact.getTag(), driftArtifact.getSbom().getTool(),
            driftArtifact.getTag()));
      }

      if (EmptyPredicate.isNotEmpty(driftArtifact.getSbom().getSbomFormat())
          && !driftArtifact.getSbom().getSbomFormat().equals(baseArtifact.getSbom().getSbomFormat())) {
        throw new InvalidRequestException(String.format(
            "Not proceeding with drift because sbom format %s for base tag %s does not match with sbom format %s of tag %s",
            baseArtifact.getSbom().getSbomFormat(), baseArtifact.getTag(), driftArtifact.getSbom().getSbomFormat(),
            driftArtifact.getTag()));
      }
    }
  }

  @Override
  public ArtifactSbomDriftResponse calculateSbomDriftForOrchestration(
      String accountId, String orgId, String projectId, String orchestrationId, DriftBase driftBase) {
    Optional<ArtifactEntity> optionalArtifactEntity =
        artifactService.getArtifact(accountId, orgId, projectId, orchestrationId);
    if (optionalArtifactEntity.isEmpty()) {
      throw new InvalidRequestException("Could not find artifact with orchestration id: " + orchestrationId);
    }

    ArtifactEntity driftArtifact = optionalArtifactEntity.get();
    ArtifactEntity baseArtifact;
    if (driftBase == DriftBase.BASELINE) {
      BaselineDTO baselineDTO =
          baselineService.getBaselineForArtifact(accountId, orgId, projectId, driftArtifact.getArtifactId());
      baseArtifact = artifactService.getLatestArtifact(
          accountId, orgId, projectId, baselineDTO.getArtifactId(), baselineDTO.getTag());
    } else {
      baseArtifact = artifactService.getLastGeneratedArtifactFromTime(
          accountId, orgId, projectId, driftArtifact.getArtifactId(), driftArtifact.getCreatedOn());
    }
    if (baseArtifact == null) {
      throw new InvalidRequestException(
          String.format("Could not find %s artifact for artifact name %s", driftBase, driftArtifact.getName()));
    }

    return calculateAndStoreSbomDrift(driftArtifact, baseArtifact, driftBase);
  }

  @Override
  public ComponentDriftResults getComponentDrifts(String accountId, String orgId, String projectId, String driftId,
      ComponentDriftStatus status, Pageable pageable, String searchTerm) {
    Criteria criteria = Criteria.where("_id").is(driftId);
    if (!sbomDriftRepository.exists(criteria)) {
      throw new InvalidRequestException("Couldn't find the drift with drift ID " + driftId);
    }

    int page = pageable.getPageNumber();
    int pageSize = pageable.getPageSize();
    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.project(DriftEntityKeys.componentDrifts, "_id"), Aggregation.unwind("$componentDrifts"),
        Aggregation.match(getComponentNameAndStatusMatchCriteria(status, searchTerm)),
        Aggregation.group("$_id").count().as("totalComponentDrifts").push("componentDrifts").as("componentDrifts"),
        Aggregation.project("totalComponentDrifts")
            .and("componentDrifts")
            .slice(pageSize, page * pageSize)
            .as("componentDrifts")
            .andExclude("_id"));

    List<ComponentDriftResults> componentDriftResults =
        sbomDriftRepository.aggregate(aggregation, ComponentDriftResults.class);

    // this would mean that there is something wrong with the component drift query, do recheck
    if (componentDriftResults == null) {
      throw new InvalidRequestException("Unable to find component drifts");
    }

    // If result list is empty, it means query was correct but returned empty results.
    if (componentDriftResults.isEmpty()) {
      return ComponentDriftResults.builder().totalComponentDrifts(0).componentDrifts(new ArrayList<>()).build();
    }

    // By nature of our query, componentDriftResults will only have one element
    return componentDriftResults.get(0);
  }

  @Override
  public LicenseDriftResults getLicenseDrifts(String accountId, String orgId, String projectId, String driftId,
      LicenseDriftStatus status, Pageable pageable, String searchTerm) {
    Criteria criteria = Criteria.where("_id").is(driftId);
    DriftEntity driftEntity = sbomDriftRepository.find(criteria);
    if (driftEntity == null) {
      throw new InvalidRequestException("Couldn't find the drift with drift ID " + driftId);
    }

    int page = pageable.getPageNumber();
    int pageSize = pageable.getPageSize();
    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.project(DriftEntityKeys.licenseDrifts, "_id"), Aggregation.unwind("$licenseDrifts"),
        Aggregation.match(getLicenseNameAndStatusMatchCriteria(status, searchTerm)),
        Aggregation.group("$_id").count().as("totalLicenseDrifts").push("licenseDrifts").as("licenseDrifts"),
        Aggregation.project("totalLicenseDrifts")
            .and("licenseDrifts")
            .slice(pageSize, page * pageSize)
            .as("licenseDrifts")
            .andExclude("_id"));

    List<LicenseDriftResults> licenseDriftResults =
        sbomDriftRepository.aggregate(aggregation, LicenseDriftResults.class);

    if (licenseDriftResults == null) {
      throw new InvalidRequestException("Unable to find license drifts");
    }

    // If result list is empty, it means query was correct but returned empty results.
    if (licenseDriftResults.isEmpty()) {
      return LicenseDriftResults.builder().totalLicenseDrifts(0).licenseDrifts(new ArrayList<>()).build();
    }

    // By nature of our query, componentDriftResults will only have one element
    LicenseDriftResults licenseDriftResult = licenseDriftResults.get(0);
    populateComponentsForLicenseDrifts(
        licenseDriftResult.getLicenseDrifts(), driftEntity.getOrchestrationId(), driftEntity.getBaseOrchestrationId());
    return licenseDriftResult;
  }

  @Override
  public OrchestrationDriftSummary getSbomDriftSummary(
      String accountId, String orgId, String projectId, String orchestrationId) {
    Criteria criteria = Criteria.where(DriftEntityKeys.accountIdentifier)
                            .is(accountId)
                            .and(DriftEntityKeys.orgIdentifier)
                            .is(orgId)
                            .and(DriftEntityKeys.projectIdentifier)
                            .is(projectId)
                            .and(DriftEntityKeys.orchestrationId)
                            .is(orchestrationId)
                            .and(DriftEntityKeys.base)
                            .ne(DriftBase.MANUAL);

    Query query = new Query(criteria);
    query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

    DriftEntity driftEntity = sbomDriftRepository.findOne(query);

    return buildDriftSummaryFromEntity(driftEntity);
  }

  private Criteria getComponentNameAndStatusMatchCriteria(ComponentDriftStatus status, String searchTerm) {
    Criteria criteria = new Criteria();
    if (status != null) {
      criteria.and(DriftEntityKeys.COMPONENT_DRIFT_STATUS).is(status.name());
    }
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      criteria.orOperator(Criteria.where(DriftEntityKeys.OLD_COMPONENT_DRIFT_NAME).regex(searchTerm, "i"),
          Criteria.where(DriftEntityKeys.NEW_COMPONENT_DRIFT_NAME).regex(searchTerm, "i"));
    }
    return criteria;
  }

  private Criteria getLicenseNameAndStatusMatchCriteria(LicenseDriftStatus status, String searchTerm) {
    Criteria criteria = new Criteria();
    if (status != null) {
      criteria.and(DriftEntityKeys.LICENSE_DRIFT_STATUS).is(status.name());
    }
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      criteria.and(DriftEntityKeys.LICENSE_DRIFT_NAME).regex(searchTerm, "i");
    }
    return criteria;
  }

  private void populateComponentsForLicenseDrifts(
      List<LicenseDrift> licenseDrifts, String orchestrationId, String baseOrchestrationId) {
    if (EmptyPredicate.isEmpty(licenseDrifts)) {
      return;
    }
    // Querying for each license because we are not expecting drastic changes in licenses.
    for (LicenseDrift licenseDrift : licenseDrifts) {
      if (LicenseDriftStatus.ADDED.equals(licenseDrift.getStatus())) {
        List<ComponentSummary> componentSummaries =
            normalisedSbomComponentService.getComponentsOfSbomByLicense(orchestrationId, licenseDrift.getName())
                .stream()
                .map(c
                    -> ComponentSummary.builder()
                           .packageName(c.getPackageName())
                           .packageVersion(c.getPackageVersion())
                           .packageLicense(c.getPackageLicense())
                           .packageOriginatorName(c.getPackageOriginatorName())
                           .packageManager(c.getPackageManager())
                           .purl(c.getPurl())
                           .build())
                .collect(Collectors.toList());
        licenseDrift.setComponents(componentSummaries);
      } else if (LicenseDriftStatus.DELETED.equals(licenseDrift.getStatus())) {
        List<ComponentSummary> componentSummaries =
            normalisedSbomComponentService.getComponentsOfSbomByLicense(baseOrchestrationId, licenseDrift.getName())
                .stream()
                .map(c
                    -> ComponentSummary.builder()
                           .packageName(c.getPackageName())
                           .packageVersion(c.getPackageVersion())
                           .packageLicense(c.getPackageLicense())
                           .packageOriginatorName(c.getPackageOriginatorName())
                           .packageManager(c.getPackageManager())
                           .purl(c.getPurl())
                           .build())
                .collect(Collectors.toList());
        licenseDrift.setComponents(componentSummaries);
      }
    }
  }

  private OrchestrationDriftSummary buildDriftSummaryFromEntity(DriftEntity driftEntity) {
    OrchestrationDriftSummary driftSummary = null;

    if (driftEntity != null) {
      int licenseDrifts = isNotEmpty(driftEntity.getLicenseDrifts()) ? driftEntity.getLicenseDrifts().size() : 0;
      int componentDrifts = isNotEmpty(driftEntity.getComponentDrifts()) ? driftEntity.getComponentDrifts().size() : 0;
      int componentsAdded = 0;
      int componentsDeleted = 0;
      int componentsModified = 0;
      int licenseAdded = 0;
      int licenseDeleted = 0;

      if (isNotEmpty(driftEntity.getComponentDrifts())) {
        for (ComponentDrift componentDrift : driftEntity.getComponentDrifts()) {
          ComponentDriftStatus status = componentDrift.getStatus();

          if (status.equals(ComponentDriftStatus.ADDED)) {
            componentsAdded++;
          } else if (status.equals(ComponentDriftStatus.DELETED)) {
            componentsDeleted++;
          } else {
            componentsModified++;
          }
        }
      }

      if (isNotEmpty(driftEntity.getLicenseDrifts())) {
        for (LicenseDrift licenseDrift : driftEntity.getLicenseDrifts()) {
          LicenseDriftStatus status = licenseDrift.getStatus();

          if (status.equals(LicenseDriftStatus.ADDED)) {
            licenseAdded++;
          } else {
            licenseDeleted++;
          }
        }
      }

      driftSummary = new OrchestrationDriftSummary()
                         .driftId(driftEntity.getUuid())
                         .base(driftEntity.getBase().toString())
                         .baseTag(driftEntity.getBaseTag())
                         .totalDrifts(licenseDrifts + componentDrifts)
                         .licenseDrifts(licenseDrifts)
                         .componentDrifts(componentDrifts)
                         .componentsAdded(componentsAdded)
                         .componentsDeleted(componentsDeleted)
                         .componentsModified(componentsModified)
                         .licenseAdded(licenseAdded)
                         .licenseDeleted(licenseDeleted);
    }
    return driftSummary;
  }
}
