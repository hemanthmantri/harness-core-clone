/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services.user;

import io.harness.exception.UnexpectedException;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.user.UserInfo;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserServiceImpl implements UserService {
  @Inject UserClient userClient;
  @Override
  public List<UserInfo> getUsersWithIds(String accountId, List<String> usersIds) {
    try {
      log.info("Fetching user info for accountId {} userIds {}", accountId, usersIds);
      List<UserInfo> userInfos =
          SafeHttpCall
              .executeWithExceptions(userClient.listUsers(accountId, UserFilterNG.builder().userIds(usersIds).build()))
              .getResource();
      log.info("Successfully fetched user info for accountId {} userIds {}", accountId, usersIds);
      return userInfos;
    } catch (IOException e) {
      log.error("Successfully fetched user info for accountId {} userIds {}", accountId, usersIds, e);
      throw new UnexpectedException("Error while fetching user info for userIds", e);
    }
  }
}