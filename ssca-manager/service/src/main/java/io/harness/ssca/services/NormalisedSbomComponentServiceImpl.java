/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ComponentFilter;
import io.harness.spec.server.ssca.v1.model.ComponentFilter.FieldNameEnum;
import io.harness.spec.server.ssca.v1.model.LicenseFilter;
import io.harness.spec.server.ssca.v1.model.NormalizedSbomComponentDTO;
import io.harness.spec.server.ssca.v1.model.Operator;
import io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields.VersionField;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.transformers.NormalisedSbomComponentTransformer;
import io.harness.ssca.utils.OperatorUtils;
import io.harness.utils.ApiUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class NormalisedSbomComponentServiceImpl implements NormalisedSbomComponentService {
  @Inject SBOMComponentRepo sbomComponentRepo;
  @Inject ArtifactService artifactService;
  Map<ComponentFilter.FieldNameEnum, String> componentFilterToFieldNameMap =
      Map.of(ComponentFilter.FieldNameEnum.COMPONENTNAME, NormalizedSBOMEntityKeys.packageName,
          ComponentFilter.FieldNameEnum.COMPONENTVERSION, NormalizedSBOMEntityKeys.packageVersion);

  @Override
  public List<NormalizedSBOMComponentEntity> getNormalizedSbomComponentsForOrchestrationId(String accountId,
      String orgIdentifier, String projectIdentifier, String orchestrationId, List<String> fieldsToBeIncluded) {
    Query query = new Query();
    Criteria criteria = Criteria.where(NormalizedSBOMEntityKeys.accountId)
                            .is(accountId)
                            .and(NormalizedSBOMEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(NormalizedSBOMEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(NormalizedSBOMEntityKeys.orchestrationId)
                            .is(orchestrationId);
    query.addCriteria(criteria);
    if (CollectionUtils.isNotEmpty(fieldsToBeIncluded)) {
      query.fields().include(fieldsToBeIncluded.toArray(new String[0]));
    }
    return sbomComponentRepo.findAllByQuery(query);
  }

  @Override
  public Response listNormalizedSbomComponent(
      String orgIdentifier, String projectIdentifier, Integer page, Integer limit, Artifact body, String accountId) {
    Pageable pageRequest = PageRequest.of(page, limit);
    String artifactId = artifactService.generateArtifactId(body.getRegistryUrl(), body.getName());
    ArtifactEntity artifact =
        artifactService
            .getArtifact(accountId, orgIdentifier, projectIdentifier, artifactId,
                Sort.by(ArtifactEntityKeys.createdOn).descending())
            .orElseThrow(()
                             -> new NotFoundException(
                                 String.format("Artifact with image name [%s] and registry Url [%s] is not found",
                                     body.getName(), body.getRegistryUrl())));
    Page<NormalizedSBOMComponentEntity> entities =
        sbomComponentRepo.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndOrchestrationId(
            accountId, orgIdentifier, projectIdentifier, artifact.getOrchestrationId(), pageRequest);
    Page<NormalizedSbomComponentDTO> result = entities.map(entity -> NormalisedSbomComponentTransformer.toDTO(entity));
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, entities.getTotalElements(), page, limit);
    return responseBuilderWithLinks.entity(result.getContent()).build();
  }

  public Criteria getLicenseCriteria(LicenseFilter licenseFilter) {
    if (licenseFilter == null) {
      return new Criteria();
    }
    switch (licenseFilter.getOperator()) {
      case EQUALS:
        return Criteria.where(NormalizedSBOMEntityKeys.packageLicense).is(licenseFilter.getValue());
      case CONTAINS:
        return Criteria.where(NormalizedSBOMEntityKeys.packageLicense).regex(licenseFilter.getValue());
      case STARTSWITH:
        return Criteria.where(NormalizedSBOMEntityKeys.packageLicense)
            .regex(Pattern.compile("^".concat(licenseFilter.getValue())));
      default:
        throw new InvalidRequestException("Invalid component filter operator");
    }
  }

  public Criteria getComponentCriteria(List<ComponentFilter> componentFilter) {
    Criteria componentCriteria = new Criteria();

    if (isNotEmpty(componentFilter)) {
      Criteria[] componentFilterCriteria = componentFilter.stream()
                                               .map(filter -> {
                                                 if (filter.getFieldName() == FieldNameEnum.COMPONENTNAME) {
                                                   return getComponentNameFilterCriteria(filter);
                                                 } else if (filter.getFieldName() == FieldNameEnum.COMPONENTVERSION) {
                                                   return getComponentVersionFilterCriteria(filter);
                                                 } else {
                                                   throw new InvalidRequestException("fieldName cannot be null");
                                                 }
                                               })
                                               .toArray(Criteria[] ::new);
      if (isNotEmpty(componentFilterCriteria)) {
        componentCriteria.andOperator(componentFilterCriteria);
      }
    }
    return componentCriteria;
  }

  private Criteria getComponentNameFilterCriteria(ComponentFilter filter) {
    String fieldName = componentFilterToFieldNameMap.get(filter.getFieldName());
    switch (filter.getOperator()) {
      case EQUALS:
        return Criteria.where(fieldName).is(filter.getValue());
      case CONTAINS:
        return Criteria.where(fieldName).regex(filter.getValue());
      case STARTSWITH:
        return Criteria.where(fieldName).regex(Pattern.compile("^".concat(filter.getValue())));
      default:
        throw new InvalidRequestException(
            String.format("Component version filter does not support %s operator", filter.getOperator()));
    }
  }

  @VisibleForTesting
  Criteria getComponentVersionFilterCriteria(ComponentFilter filter) {
    List<Integer> versions = VersionField.getVersion(filter.getValue());
    if (versions.size() != 3 || versions.get(0) == -1) {
      throw new InvalidArgumentsException(String.format("Unsupported Version Format"));
    }
    if (filter.getOperator() != Operator.EQUALS) {
      Criteria patchCriteria = new Criteria();
      Criteria minorCriteria = new Criteria();
      Criteria majorCriteria = new Criteria();
      if (versions.get(2) != -1) {
        patchCriteria =
            getPatchVersionCriteria(versions.get(0), versions.get(1), versions.get(2), filter.getOperator());
      }
      if (versions.get(1) != -1) {
        minorCriteria = getMinorVersionCriteria(versions.get(0), versions.get(1), filter.getOperator());
      }
      if (versions.get(0) != -1) {
        majorCriteria = getMajorVersionCriteria(versions.get(0), filter.getOperator());
      }
      return new Criteria().orOperator(patchCriteria, minorCriteria, majorCriteria);
    }
    return getEqualVersionCriteria(versions);
  }

  private Criteria getEqualVersionCriteria(List<Integer> versions) {
    return new Criteria()
        .where(NormalizedSBOMEntityKeys.majorVersion)
        .is(versions.get(0))
        .and(NormalizedSBOMEntityKeys.minorVersion)
        .is(versions.get(1))
        .and(NormalizedSBOMEntityKeys.patchVersion)
        .is(versions.get(2));
  }

  private Criteria getPatchVersionCriteria(int majorVersion, int minorVersion, int patchVersion, Operator operator) {
    Criteria patchCriteria = new Criteria()
                                 .where(NormalizedSBOMEntityKeys.majorVersion)
                                 .is(majorVersion)
                                 .and(NormalizedSBOMEntityKeys.minorVersion)
                                 .is(minorVersion);
    Criteria operatorCriteria =
        OperatorUtils.getCriteria(operator, NormalizedSBOMEntityKeys.patchVersion, patchVersion);
    if (operator == Operator.NOTEQUALS) {
      return operatorCriteria;
    }
    return patchCriteria.andOperator(operatorCriteria);
  }

  private Criteria getMinorVersionCriteria(int majorVersion, int minorVersion, Operator operator) {
    Criteria minorCriteria = new Criteria().where(NormalizedSBOMEntityKeys.majorVersion).is(majorVersion);
    Operator queryOperator = OperatorUtils.getComponentFilterOperatorMapping(operator);
    Criteria operatorCriteria =
        OperatorUtils.getCriteria(queryOperator, NormalizedSBOMEntityKeys.minorVersion, minorVersion);
    if (operator == Operator.NOTEQUALS) {
      return operatorCriteria;
    }
    return minorCriteria.andOperator(operatorCriteria);
  }

  private Criteria getMajorVersionCriteria(int majorVersion, Operator operator) {
    Operator queryOperator = OperatorUtils.getComponentFilterOperatorMapping(operator);
    return OperatorUtils.getCriteria(queryOperator, NormalizedSBOMEntityKeys.majorVersion, majorVersion);
  }

  public Page<NormalizedSBOMComponentEntity> getNormalizedSbomComponents(String accountId, String orgIdentifier,
      String projectIdentifier, ArtifactEntity artifact, ArtifactComponentViewRequestBody filterBody,
      Pageable pageable) {
    Criteria criteria = Criteria.where(NormalizedSBOMEntityKeys.accountId)
                            .is(accountId)
                            .and(NormalizedSBOMEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(NormalizedSBOMEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(NormalizedSBOMEntityKeys.orchestrationId)
                            .is(artifact.getOrchestrationId());
    if (Objects.nonNull(filterBody)) {
      if (Objects.nonNull(filterBody.getPackageManager())) {
        Pattern pattern = Pattern.compile("[.]*" + filterBody.getPackageManager() + "[.]*");
        criteria.and(NormalizedSBOMEntityKeys.packageManager).regex(pattern);
      }
      if (Objects.nonNull(filterBody.getPackageSupplier())) {
        Pattern pattern = Pattern.compile("[.]*" + filterBody.getPackageSupplier() + "[.]*");
        criteria.and(NormalizedSBOMEntityKeys.packageOriginatorName).regex(pattern);
      }

      criteria.andOperator(
          getLicenseCriteria(filterBody.getLicenseFilter()), getComponentCriteria(filterBody.getComponentFilter()));
    }

    return sbomComponentRepo.findAll(criteria, pageable);
  }

  public List<String> getOrchestrationIds(String accountId, String orgIdentifier, String projectIdentifier,
      LicenseFilter licenseFilter, List<ComponentFilter> componentFilter) {
    Criteria criteria = Criteria.where(NormalizedSBOMEntityKeys.accountId)
                            .is(accountId)
                            .and(NormalizedSBOMEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(NormalizedSBOMEntityKeys.projectIdentifier)
                            .is(projectIdentifier);

    criteria.andOperator(getLicenseCriteria(licenseFilter), getComponentCriteria(componentFilter));
    return sbomComponentRepo.findDistinctOrchestrationIds(criteria);
  }

  @Override
  public <T> List<T> getComponentsByAggregation(Aggregation aggregation, Class<T> resultClass) {
    return sbomComponentRepo.aggregate(aggregation, resultClass);
  }

  @Override
  public List<NormalizedSBOMComponentEntity> getComponentsOfSbomByLicense(String orchestrationId, String license) {
    Criteria criteria = Criteria.where(NormalizedSBOMEntityKeys.orchestrationId.toLowerCase())
                            .is(orchestrationId)
                            .and(NormalizedSBOMEntityKeys.packageLicense.toLowerCase())
                            .is(license);

    return sbomComponentRepo.findAllByQuery(new Query(criteria));
  }
}
