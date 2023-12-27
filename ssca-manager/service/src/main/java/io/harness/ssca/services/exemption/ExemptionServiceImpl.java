/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services.exemption;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.user.UserInfo;
import io.harness.persistence.UserProvider;
import io.harness.repositories.exemption.ExemptionRepository;
import io.harness.spec.server.ssca.v1.model.ExemptionRequestDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionResponseDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionReviewRequestDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionStatusDTO;
import io.harness.ssca.entities.exemption.Exemption;
import io.harness.ssca.entities.exemption.Exemption.ExemptionDuration;
import io.harness.ssca.entities.exemption.Exemption.ExemptionKeys;
import io.harness.ssca.entities.exemption.Exemption.ExemptionStatus;
import io.harness.ssca.mapper.ExemptionMapper;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.support.PageableExecutionUtils;

@OwnedBy(HarnessTeam.SSCA)
@Slf4j
public class ExemptionServiceImpl implements ExemptionService {
  @Inject ExemptionRepository exemptionRepository;
  @Inject UserProvider userProvider;
  @Inject UserClient userClient;
  @Override
  public Page<ExemptionResponseDTO> getExemptions(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, List<ExemptionStatusDTO> exemptionStatusDTOs, String searchTerm, Pageable pageable) {
    Criteria criteria = Criteria.where(ExemptionKeys.accountId)
                            .is(accountId)
                            .and(ExemptionKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ExemptionKeys.projectIdentifier)
                            .is(projectIdentifier);
    if (StringUtils.isNotBlank(artifactId)) {
      criteria = criteria.and(ExemptionKeys.artifactId).is(artifactId);
    }
    if (CollectionUtils.isNotEmpty(exemptionStatusDTOs)) {
      criteria =
          criteria.and(ExemptionKeys.exemptionStatus)
              .in(exemptionStatusDTOs.stream().map(ExemptionMapper::toExemptionStatus).collect(Collectors.toList()));
    }
    if (StringUtils.isNotBlank(searchTerm)) {
      criteria = criteria.and(ExemptionKeys.componentName).regex(searchTerm, "i");
    }
    Page<Exemption> exemptionPage = exemptionRepository.findExemptions(criteria, pageable);
    List<ExemptionResponseDTO> exemptionResponseDTOs =
        ExemptionMapper.toExemptionResponseDTOs(exemptionPage.getContent(), fetchUsers(exemptionPage.getContent()));
    return PageableExecutionUtils.getPage(exemptionResponseDTOs, pageable, exemptionPage::getTotalElements);
  }

  @Override
  public ExemptionResponseDTO getExemption(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String exemptionId) {
    Exemption exemption =
        getExemptionByScopeAndUuid(accountId, orgIdentifier, projectIdentifier, artifactId, exemptionId);
    return ExemptionMapper.toExemptionResponseDTO(exemption, fetchUsers(List.of(exemption)));
  }

  @Override
  public ExemptionResponseDTO createExemption(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, ExemptionRequestDTO exemptionRequestDTO) {
    EmbeddedUser embeddedUser = userProvider.activeUser();
    List<Exemption> existingExemptions = getExistingExemptionsForUserIdInCurrentScope(
        accountId, orgIdentifier, projectIdentifier, artifactId, exemptionRequestDTO, embeddedUser);
    Exemption createdExemption;
    if (CollectionUtils.isNotEmpty(existingExemptions)) {
      log.info("Create new exemption: Returning existing exemption {} for accountId {} orgIdentifier {} "
              + "projectIdentifier {} artifactId {} componentName {} componentVersion {} createdBy {} exemptionStatus PENDING",
          existingExemptions.get(0).getUuid(), accountId, orgIdentifier, projectIdentifier, artifactId,
          exemptionRequestDTO.getComponentName(), exemptionRequestDTO.getComponentVersion(), embeddedUser.getUuid());
      createdExemption = existingExemptions.get(0);
    } else {
      createdExemption = createAndGetNewExemption(
          accountId, orgIdentifier, projectIdentifier, artifactId, exemptionRequestDTO, embeddedUser);
    }
    return ExemptionMapper.toExemptionResponseDTO(createdExemption, fetchUsers(List.of(createdExemption)));
  }

  @Override
  public ExemptionResponseDTO updateExemption(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, String exemptionId, ExemptionRequestDTO exemptionRequestDTO) {
    EmbeddedUser embeddedUser = userProvider.activeUser();
    Exemption existingExemption =
        getExemptionByScopeAndUuid(accountId, orgIdentifier, projectIdentifier, artifactId, exemptionId);
    validateCurrentExemptionStatus(existingExemption, ExemptionStatus.PENDING);
    existingExemption = ExemptionMapper.toExemption(exemptionRequestDTO, existingExemption)
                            .toBuilder()
                            .updatedBy(embeddedUser.getUuid())
                            .build();
    Exemption updatedExemption = exemptionRepository.save(existingExemption);
    return ExemptionMapper.toExemptionResponseDTO(updatedExemption, fetchUsers(List.of(updatedExemption)));
  }

  @Override
  public void deleteExemption(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String exemptionId) {
    Exemption exemption =
        getExemptionByScopeAndUuid(accountId, orgIdentifier, projectIdentifier, artifactId, exemptionId);
    validateCurrentExemptionStatus(exemption, ExemptionStatus.PENDING);
    exemptionRepository.deleteById(exemptionId);
  }

  @Override
  public ExemptionResponseDTO reviewExemption(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, String exemptionId, ExemptionReviewRequestDTO exemptionReviewRequestDTO) {
    if (ExemptionStatusDTO.PENDING.equals(exemptionReviewRequestDTO.getExemptionStatus())) {
      throw new BadRequestException("Review status should not be PENDING");
    }
    Instant now = Instant.now();
    EmbeddedUser embeddedUser = userProvider.activeUser();
    Exemption existingExemption =
        getExemptionByScopeAndUuid(accountId, orgIdentifier, projectIdentifier, artifactId, exemptionId);

    ExemptionStatus updatedStatus = ExemptionMapper.toExemptionStatus(exemptionReviewRequestDTO.getExemptionStatus());
    existingExemption.setExemptionStatus(updatedStatus);
    existingExemption.setReviewedBy(embeddedUser.getUuid());
    existingExemption.setReviewedAt(now.toEpochMilli());
    existingExemption.setReviewComment(exemptionReviewRequestDTO.getReviewComment());
    if (updatedStatus.equals(ExemptionStatus.APPROVED)) {
      existingExemption.setValidUntil(getValidUntilValue(now, existingExemption.getExemptionDuration()));
    } else {
      existingExemption.setValidUntil(0L);
    }
    Exemption updatedExemption = exemptionRepository.save(existingExemption);
    return ExemptionMapper.toExemptionResponseDTO(updatedExemption, fetchUsers(List.of(updatedExemption)));
  }

  private static Long getValidUntilValue(Instant now, ExemptionDuration exemptionDuration) {
    if (exemptionDuration.isAlwaysExempt()) {
      return Long.MAX_VALUE;
    } else {
      return now.plus(exemptionDuration.getDays(), ChronoUnit.DAYS).toEpochMilli();
    }
  }

  private Map<String, String> fetchUsers(List<Exemption> exemptions) {
    Map<String, String> usersMap = null;
    if (CollectionUtils.isNotEmpty(exemptions)) {
      Set<String> userIds = new HashSet<>();
      for (Exemption exemption : exemptions) {
        userIds.add(exemption.getCreatedBy());
        userIds.add(exemption.getReviewedBy());
      }
      try {
        String accountId = exemptions.get(0).getAccountId();
        List<UserInfo> users = SafeHttpCall
                                   .executeWithExceptions(userClient.listUsers(
                                       accountId, UserFilterNG.builder().userIds(userIds.stream().toList()).build()))
                                   .getResource();
        usersMap = users.stream().collect(Collectors.toMap(UserInfo::getUuid, UserInfo::getName, (u, v) -> u));
      } catch (Exception e) {
        log.error("Error while fetching user info for userIds", e);
      }
    }
    return usersMap;
  }

  private Exemption getExemptionByScopeAndUuid(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String exemptionId) {
    Criteria criteria = Criteria.where(ExemptionKeys.accountId)
                            .is(accountId)
                            .and(ExemptionKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ExemptionKeys.projectIdentifier)
                            .is(projectIdentifier);
    if (StringUtils.isBlank(artifactId)) {
      criteria = criteria.and(ExemptionKeys.artifactId).isNull();
    } else {
      criteria = criteria.and(ExemptionKeys.artifactId).is(artifactId);
    }
    criteria = criteria.and(ExemptionKeys.uuid).is(exemptionId);
    return exemptionRepository.findExemptions(criteria).stream().findFirst().orElseThrow(
        ()
            -> new NotFoundException(String.format(
                "Exemption for accountId %s orgIdentifier %s projectIdentifier %s artifactId %s exemptionId %s not found",
                accountId, orgIdentifier, projectIdentifier, artifactId, exemptionId)));
  }

  private void validateCurrentExemptionStatus(Exemption exemption, ExemptionStatus exemptionStatus) {
    if (!exemption.getExemptionStatus().equals(exemptionStatus)) {
      throw new BadRequestException(String.format(
          "Exemption for accountId %s orgIdentifier %s projectIdentifier %s artifactId %s exemptionId %s not in %s state",
          exemption.getAccountId(), exemption.getOrgIdentifier(), exemption.getProjectIdentifier(),
          exemption.getArtifactId(), exemption.getUuid(), exemptionStatus.name()));
    }
  }

  private List<Exemption> getExistingExemptionsForUserIdInCurrentScope(String accountId, String orgIdentifier,
      String projectIdentifier, String artifactId, ExemptionRequestDTO exemptionRequestDTO, EmbeddedUser embeddedUser) {
    log.info(
        "Create new exemption: Checking existing exemptions for accountId {} orgIdentifier {} projectIdentifier {} artifactId {} "
            + "componentName {} componentVersion {} createdBy {} exemptionStatus PENDING",
        accountId, orgIdentifier, projectIdentifier, artifactId, exemptionRequestDTO.getComponentName(),
        exemptionRequestDTO.getComponentVersion(), embeddedUser.getUuid());
    Criteria criteria = Criteria.where(ExemptionKeys.accountId)
                            .is(accountId)
                            .and(ExemptionKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ExemptionKeys.projectIdentifier)
                            .is(projectIdentifier);
    if (StringUtils.isBlank(artifactId)) {
      criteria = criteria.and(ExemptionKeys.artifactId).isNull();
    } else {
      criteria = criteria.and(ExemptionKeys.artifactId).is(artifactId);
    }
    criteria = criteria.and(ExemptionKeys.componentName)
                   .is(exemptionRequestDTO.getComponentName())
                   .and(ExemptionKeys.componentVersion)
                   .is(exemptionRequestDTO.getComponentVersion())
                   .and(ExemptionKeys.createdBy)
                   .is(embeddedUser.getUuid())
                   .and(ExemptionKeys.exemptionStatus)
                   .is(ExemptionStatus.PENDING);
    List<Exemption> existingExemptions = exemptionRepository.findExemptions(criteria);
    log.info(
        "Create new exemption: Found {} existing exemptions for accountId {} orgIdentifier {} projectIdentifier {} artifactId {} "
            + "componentName {} componentVersion {} createdBy {} exemptionStatus PENDING",
        existingExemptions.size(), accountId, orgIdentifier, projectIdentifier, artifactId,
        exemptionRequestDTO.getComponentName(), exemptionRequestDTO.getComponentVersion(), embeddedUser.getUuid());
    return existingExemptions;
  }

  private Exemption createAndGetNewExemption(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, ExemptionRequestDTO exemptionRequestDTO, EmbeddedUser embeddedUser) {
    Exemption exemptionRequest = ExemptionMapper.toExemption(exemptionRequestDTO)
                                     .toBuilder()
                                     .accountId(accountId)
                                     .orgIdentifier(orgIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .artifactId(artifactId)
                                     .exemptionStatus(ExemptionStatus.PENDING)
                                     .createdBy(embeddedUser.getUuid())
                                     .updatedBy(embeddedUser.getUuid())
                                     .build();
    Exemption createdExemption = exemptionRepository.createExemption(exemptionRequest);
    log.info(
        "Created new exemption: Creating new exemption {} for accountId {} orgIdentifier {} projectIdentifier {} artifactId {} ",
        createdExemption.getUuid(), accountId, orgIdentifier, projectIdentifier, artifactId);
    return createdExemption;
  }
}
