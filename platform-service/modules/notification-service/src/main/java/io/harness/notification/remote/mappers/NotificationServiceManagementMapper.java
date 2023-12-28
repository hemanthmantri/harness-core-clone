/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.mappers;

import io.harness.notification.NotificationChannelType;
;
import io.harness.notification.entities.Channel;
import io.harness.notification.entities.EmailChannel;
import io.harness.notification.entities.MicrosoftTeamsChannel;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationCondition;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.entities.NotificationEventConfig;
import io.harness.notification.entities.NotificationRule;
import io.harness.notification.entities.PagerDutyChannel;
import io.harness.notification.entities.SlackChannel;
import io.harness.notification.entities.WebhookChannel;
import io.harness.spec.server.notification.v1.model.ChannelDTO;
import io.harness.spec.server.notification.v1.model.ChannelType;
import io.harness.spec.server.notification.v1.model.NotificationChannelDTO;
import io.harness.spec.server.notification.v1.model.NotificationRuleDTO;
import io.harness.spec.server.notification.v1.model.NotificationRuleDTONotificationChannels;
import io.harness.spec.server.notification.v1.model.NotificationRuleDTONotificationConditions;
import io.harness.spec.server.notification.v1.model.NotificationRuleDTONotificationEventConfigs;
import io.harness.spec.server.notification.v1.model.Status;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NotificationServiceManagementMapper {
  private static Map<ChannelType, NotificationChannelType> notificationChannelTypeEnumMap = new HashMap<>();
  private static Map<NotificationChannelType, ChannelType> channelTypeEnumMap = new HashMap<>();
  private static Map<Status, NotificationChannel.Status> channelStatusEnumMap = new HashMap<>();
  private static Map<NotificationChannel.Status, Status> statusEnumMap = new HashMap<>();

  static {
    notificationChannelTypeEnumMap.put(ChannelType.EMAIL, NotificationChannelType.EMAIL);
    notificationChannelTypeEnumMap.put(ChannelType.SLACK, NotificationChannelType.SLACK);
    notificationChannelTypeEnumMap.put(ChannelType.MSTEAMS, NotificationChannelType.MSTEAMS);
    notificationChannelTypeEnumMap.put(ChannelType.PAGERDUTY, NotificationChannelType.PAGERDUTY);
    notificationChannelTypeEnumMap.put(ChannelType.WEBHOOK, NotificationChannelType.WEBHOOK);
    channelTypeEnumMap = notificationChannelTypeEnumMap.entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    channelStatusEnumMap.put(Status.ENABLED, NotificationChannel.Status.ENABLED);
    channelStatusEnumMap.put(Status.DISABLED, NotificationChannel.Status.DISABLED);
    statusEnumMap =
        channelStatusEnumMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  public NotificationRule toNotificationRuleEntity(NotificationRuleDTO notificationRuleDTO) {
    return NotificationRule.builder()
        .identifier(notificationRuleDTO.getIdentifier())
        .name(notificationRuleDTO.getName())
        .accountIdentifier(notificationRuleDTO.getAccount())
        .orgIdentifier(notificationRuleDTO.getOrg())
        .projectIdentifier(notificationRuleDTO.getProject())
        .status(getNotificationRuleStatus(notificationRuleDTO))
        .notificationConditions(getNotificationConditions(notificationRuleDTO.getNotificationConditions()))
        .build();
  }

  public NotificationRuleDTO toNotificationRuleDTO(NotificationRule notificationRule) {
    return new NotificationRuleDTO()
        .account(notificationRule.getAccountIdentifier())
        .org(notificationRule.getOrgIdentifier())
        .project(notificationRule.getOrgIdentifier())
        .identifier(notificationRule.getIdentifier())
        .name(notificationRule.getName())
        .status(getNotificationRuleDTOStatus(notificationRule));
  }

  public NotificationChannel toNotificationChannelEntity(
      NotificationChannelDTO notificationChannelDTO, String accountIdentifier) {
    NotificationChannelType notificationChannelType =
        notificationChannelTypeEnumMap.get(notificationChannelDTO.getNotificationChannelType());
    return NotificationChannel.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(notificationChannelDTO.getOrg())
        .projectIdentifier(notificationChannelDTO.getProject())
        .identifier(notificationChannelDTO.getIdentifier())
        .notificationChannelType(notificationChannelType)
        .name(notificationChannelDTO.getName())
        .status(channelStatusEnumMap.get(notificationChannelDTO.getStatus()))
        .channel(toChannelEntity(notificationChannelDTO, notificationChannelType))
        .build();
  }

  public NotificationChannelDTO toNotificationChannelDTO(NotificationChannel notificationChannel) {
    return new NotificationChannelDTO()
        .identifier(notificationChannel.getIdentifier())
        .project(notificationChannel.getProjectIdentifier())
        .org(notificationChannel.getOrgIdentifier())
        .notificationChannelType(channelTypeEnumMap.get(notificationChannel.getNotificationChannelType()))
        .name(notificationChannel.getName())
        .status(statusEnumMap.get(notificationChannel.getStatus()))
        .channel(getChannel(notificationChannel));
  }

  private Channel toChannelEntity(
      NotificationChannelDTO notificationChannelDTO, NotificationChannelType notificationChannelType) {
    ChannelDTO channel = notificationChannelDTO.getChannel();
    switch (notificationChannelType) {
      case EMAIL:
        return EmailChannel.builder().emailIds(channel.getEmailIds()).build();
      case SLACK:
        return SlackChannel.builder().slackWebHookUrls(channel.getSlackWebhookUrls()).build();
      case MSTEAMS:
        return MicrosoftTeamsChannel.builder().msTeamKeys(channel.getMsTeamKeys()).build();
      case PAGERDUTY:
        return PagerDutyChannel.builder().pagerDutyIntegrationKeys(channel.getPagerDutyIntegrationKeys()).build();
      case WEBHOOK:
        return WebhookChannel.builder().webHookUrls(channel.getWebhookUrls()).build();
      default:
        return null;
    }
  }

  private List<NotificationCondition> getNotificationConditions(
      List<NotificationRuleDTONotificationConditions> notificationConditions) {
    return notificationConditions.stream().map(this::toNotificationCondition).collect(Collectors.toList());
  }

  private NotificationCondition toNotificationCondition(
      NotificationRuleDTONotificationConditions notificationConditions) {
    return NotificationCondition.builder()
        .conditionName(notificationConditions.getConditionName())
        .notificationEventConfigs(getNotificationEventConfig(notificationConditions.getNotificationEventConfigs()))
        .build();
  }

  private List<NotificationEventConfig> getNotificationEventConfig(
      List<NotificationRuleDTONotificationEventConfigs> notificationEventConfigs) {
    return notificationEventConfigs.stream().map(this::getNotificationEventConfig).collect(Collectors.toList());
  }

  private NotificationEventConfig getNotificationEventConfig(
      NotificationRuleDTONotificationEventConfigs notificationEventConfigs) {
    return NotificationEventConfig.builder()
        .notificationEntity(Enum.valueOf(NotificationEntity.class, notificationEventConfigs.getNotificationEntity()))
        .notificationEvent(Enum.valueOf(NotificationEvent.class, notificationEventConfigs.getNotificationEvent()))
        .notificationChannels(getNotificationChannel(notificationEventConfigs.getNotificationChannels()))
        .build();
  }

  private List<NotificationChannel> getNotificationChannel(
      List<NotificationRuleDTONotificationChannels> notificationRuleDTONotificationChannels) {
    return Collections.emptyList();
  }

  private NotificationRule.Status getNotificationRuleStatus(NotificationRuleDTO notificationRuleDTO) {
    if (notificationRuleDTO.getStatus().equals(Status.DISABLED)) {
      return NotificationRule.Status.DISABLED;
    }
    return NotificationRule.Status.ENABLED;
  }

  private Status getNotificationRuleDTOStatus(NotificationRule notificationRule) {
    if (notificationRule.getStatus().equals(NotificationRule.Status.DISABLED)) {
      return Status.DISABLED;
    }
    return Status.ENABLED;
  }

  private ChannelDTO getChannel(NotificationChannel notificationChannel) {
    switch (notificationChannel.getNotificationChannelType()) {
      case EMAIL:
        EmailChannel emailChannel = (EmailChannel) notificationChannel.getChannel();
        return emailChannel.dto();
      case SLACK:
        SlackChannel slackChannel = (SlackChannel) notificationChannel.getChannel();
        return slackChannel.dto();
      case PAGERDUTY:
        PagerDutyChannel pagerDutyChannel = (PagerDutyChannel) notificationChannel.getChannel();
        return pagerDutyChannel.dto();
      case MSTEAMS:
        MicrosoftTeamsChannel msTeamChannel = (MicrosoftTeamsChannel) notificationChannel.getChannel();
        return msTeamChannel.dto();
      case WEBHOOK:
        WebhookChannel webhookChannel = (WebhookChannel) notificationChannel.getChannel();
        return webhookChannel.dto();
      default:
        return null;
    }
  }
}
