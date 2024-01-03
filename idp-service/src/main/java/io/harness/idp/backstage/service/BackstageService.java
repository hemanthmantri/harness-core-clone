/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;

@OwnedBy(HarnessTeam.IDP)
public interface BackstageService {
  void sync();
  boolean sync(String accountIdentifier);
  boolean sync(String accountIdentifier, String entityUid, String action, String syncMode);
  void syncScaffolderTasks();
  void syncScaffolderTasks(String accountIdentifier, long syncFrom, NamespaceEntity namespaceEntity);
}
