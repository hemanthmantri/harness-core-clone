/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.resources.v1;

import static io.harness.notification.utils.NotificationManagementResourceTypes.NOTIFICATION_MANAGEMENT;
import static io.harness.notification.utils.NotificationManagementServicePermission.DELETE_NOTIFICATION_MANAGEMENT_PERMISSION;
import static io.harness.notification.utils.NotificationManagementServicePermission.EDIT_NOTIFICATION_MANAGEMENT_PERMISSION;
import static io.harness.notification.utils.NotificationManagementServicePermission.VIEW_NOTIFICATION_MANAGEMENT_PERMISSION;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.entities.NotificationRule;
import io.harness.notification.remote.mappers.NotificationServiceManagementMapper;
import io.harness.notification.service.api.NotificationRuleManagementService;
import io.harness.notification.utils.NotificationManagementApiUtils;
import io.harness.notification.utils.NotificationRuleFilterProperties;
import io.harness.spec.server.notification.v1.NotificationRulesApi;
import io.harness.spec.server.notification.v1.model.NotificationRuleDTO;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NotificationRuleApiImpl implements NotificationRulesApi {
  private final NotificationRuleManagementService notificationRuleManagementService;
  private final NotificationServiceManagementMapper notificationServiceManagementMapper;
  private final NotificationManagementApiUtils notificationManagementApiUtils;
  private final AccessControlClient accessControlClient;

  @Override
  public Response createNotificationRule(
      String org, String project, @Valid NotificationRuleDTO body, String harnessAccount) {
    return createNotificationRuleInternal(harnessAccount, org, project, body);
  }

  @Override
  public Response createNotificationRuleOrg(String org, @Valid NotificationRuleDTO body, String harnessAccount) {
    return createNotificationRuleInternal(harnessAccount, org, null, body);
  }

  @Override
  public Response createNotificationRuleAccount(@Valid NotificationRuleDTO body, String harnessAccount) {
    return createNotificationRuleInternal(harnessAccount, null, null, body);
  }

  @Override
  public Response getNotificationRule(String org, String project, String notificationRule, String harnessAccount) {
    return getNotificationRuleInternal(harnessAccount, org, project, notificationRule);
  }

  @Override
  public Response getNotificationRuleOrg(String org, String notificationRule, String harnessAccount) {
    return getNotificationRuleInternal(harnessAccount, org, null, notificationRule);
  }

  @Override
  public Response getNotificationRuleAccount(String notificationRule, String harnessAccount) {
    return getNotificationRuleInternal(harnessAccount, null, null, notificationRule);
  }

  @Override
  public Response listNotificationRules(String org, String project, String harnessAccount, Integer page,
      @Max(1000L) Integer limit, String sort, String order, String searchTerm) {
    return listNotificationRulesInternal(harnessAccount, org, project, page, limit, sort, order, searchTerm);
  }

  @Override
  public Response listNotificationRulesOrg(String org, String harnessAccount, Integer page, @Max(1000L) Integer limit,
      String sort, String order, String searchTerm) {
    return listNotificationRulesInternal(harnessAccount, org, null, page, limit, sort, order, searchTerm);
  }

  @Override
  public Response listNotificationRulesAccount(
      String harnessAccount, Integer page, @Max(1000L) Integer limit, String sort, String order, String searchTerm) {
    return listNotificationRulesInternal(harnessAccount, null, null, page, limit, sort, order, searchTerm);
  }

  @Override
  public Response updateNotificationRule(
      String org, String project, String notificationRule, @Valid NotificationRuleDTO body, String harnessAccount) {
    return updateNotificationRuleInternal(harnessAccount, org, project, notificationRule, body);
  }

  @Override
  public Response updateNotificationRuleOrg(
      String org, String notificationRule, @Valid NotificationRuleDTO body, String harnessAccount) {
    return updateNotificationRuleInternal(harnessAccount, org, null, notificationRule, body);
  }

  @Override
  public Response updateNotificationRuleAccount(
      String notificationRule, @Valid NotificationRuleDTO body, String harnessAccount) {
    return updateNotificationRuleInternal(harnessAccount, null, null, notificationRule, body);
  }

  @Override
  public Response deleteNotificationRule(String org, String project, String notificationRule, String harnessAccount) {
    return deleteNotificationRuleInternal(harnessAccount, org, project, notificationRule);
  }

  @Override
  public Response deleteNotificationRuleOrg(String org, String notificationRule, String harnessAccount) {
    return deleteNotificationRuleInternal(harnessAccount, org, null, notificationRule);
  }

  @Override
  public Response deleteNotificationRuleAccount(String notificationRule, String harnessAccount) {
    return deleteNotificationRuleInternal(harnessAccount, null, null, notificationRule);
  }

  private Response createNotificationRuleInternal(
      String accountId, String orgId, String projectId, NotificationRuleDTO notificationRuleDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(NOTIFICATION_MANAGEMENT, null), EDIT_NOTIFICATION_MANAGEMENT_PERMISSION);
    NotificationRule notificationRule = notificationRuleManagementService.create(
        notificationServiceManagementMapper.toNotificationRuleEntity(notificationRuleDTO));
    return Response.status(Response.Status.CREATED)
        .entity(notificationServiceManagementMapper.toNotificationRuleDTO(notificationRule))
        .build();
  }

  private Response getNotificationRuleInternal(
      String accountId, String orgId, String projectId, String notificationRuleIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(NOTIFICATION_MANAGEMENT, null), VIEW_NOTIFICATION_MANAGEMENT_PERMISSION);
    NotificationRule notificationRuleEntity =
        notificationRuleManagementService.get(accountId, orgId, projectId, notificationRuleIdentifier);
    return Response.status(Response.Status.OK)
        .entity(notificationServiceManagementMapper.toNotificationRuleDTO(notificationRuleEntity))
        .build();
  }

  private Response listNotificationRulesInternal(String accountId, String orgId, String projectId, Integer page,
      Integer limit, String sort, String order, String searchTerm) {
    NotificationRuleFilterProperties filterProperties =
        notificationManagementApiUtils.getNotificationRuleFilterProperties(
            searchTerm, NotificationEntity.DELEGATE, NotificationEvent.DELEGATE_DOWN);
    Pageable pageable = notificationManagementApiUtils.getPageRequest(1, limit, sort, order);
    Page<NotificationRule> notificationRulePage =
        notificationRuleManagementService.list(accountId, orgId, projectId, pageable, filterProperties);
    Page<NotificationRuleDTO> notificationRuleResponsePage =
        notificationRulePage.map(notificationServiceManagementMapper::toNotificationRuleDTO);
    return Response.ok().entity(notificationRuleResponsePage.getContent()).build();
  }

  private Response updateNotificationRuleInternal(String accountId, String orgId, String projectId,
      String notificationRuleIdentifier, NotificationRuleDTO notificationRuleDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(NOTIFICATION_MANAGEMENT, null), EDIT_NOTIFICATION_MANAGEMENT_PERMISSION);
    NotificationRule existingEntity =
        notificationRuleManagementService.get(accountId, null, null, notificationRuleIdentifier);
    NotificationRule entityToUpdate = notificationServiceManagementMapper.toNotificationRuleEntity(notificationRuleDTO);
    entityToUpdate.setUuid(existingEntity.getUuid());
    NotificationRule notificationRule = notificationRuleManagementService.update(entityToUpdate);
    return Response.status(Response.Status.CREATED)
        .entity(notificationServiceManagementMapper.toNotificationRuleDTO(notificationRule))
        .build();
  }

  private Response deleteNotificationRuleInternal(
      String accountId, String orgId, String projectId, String notificationRuleIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(NOTIFICATION_MANAGEMENT, null), DELETE_NOTIFICATION_MANAGEMENT_PERMISSION);
    NotificationRule notificationRuleToDelete =
        notificationRuleManagementService.get(accountId, orgId, projectId, notificationRuleIdentifier);
    notificationRuleManagementService.delete(notificationRuleToDelete);
    return Response.status(Response.Status.OK).entity(notificationRuleIdentifier).build();
  }
}
