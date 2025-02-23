/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service.api;

import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.utils.NotificationChannelFilterProperties;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationChannelManagementService {
  NotificationChannel create(@Valid NotificationChannel notificationChannel);

  NotificationChannel update(@Valid NotificationChannel notificationChannel);

  NotificationChannel get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String notificationRuleNameIdentifier);

  List<NotificationChannel> getNotificationChannelList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);

  boolean delete(@Valid NotificationChannel notificationChannel);

  Page<NotificationChannel> list(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Pageable pageable, NotificationChannelFilterProperties notificationManagementServiceFilterProperties);
}
