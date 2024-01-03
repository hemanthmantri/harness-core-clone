/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.common.DateUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.IDP)
public class BackstageScaffolderTask {
  @JsonProperty("id") @NotNull String identifier;
  @NotNull String spec;
  @NotNull String status;
  @NotNull @JsonProperty("created_at") String createdAt;
  @JsonProperty("last_heartbeat_at") String lastHeartbeatAt;
  String secrets;
  @JsonProperty("created_by") String createdBy;

  private static final String TIMESTAMP_WITH_TIMEZONE = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  public static List<BackstageScaffolderTaskEntity> toEntities(String accountIdentifier,
      List<BackstageScaffolderTask> backstageScaffolderTasks,
      BackstageScaffolderTaskEntityRepository scaffolderTasksEntityRepository) {
    List<BackstageScaffolderTaskEntity> backstageScaffolderTasksEntities = new ArrayList<>();
    backstageScaffolderTasks.forEach(backstageScaffolderTask -> {
      BackstageScaffolderTaskEntity backstageScaffolderTaskEntity = new BackstageScaffolderTaskEntity();

      Optional<BackstageScaffolderTaskEntity> optionalBackstageScaffolderTaskEntity =
          scaffolderTasksEntityRepository.findByAccountIdentifierAndIdentifier(
              accountIdentifier, backstageScaffolderTask.getIdentifier());
      optionalBackstageScaffolderTaskEntity.ifPresent(existingBackstageScaffolderTaskEntity
          -> backstageScaffolderTaskEntity.setId(existingBackstageScaffolderTaskEntity.getId()));

      backstageScaffolderTaskEntity.setAccountIdentifier(accountIdentifier);
      backstageScaffolderTaskEntity.setIdentifier(backstageScaffolderTask.getIdentifier());
      backstageScaffolderTaskEntity.setSpec(backstageScaffolderTask.getSpec());
      backstageScaffolderTaskEntity.setStatus(backstageScaffolderTask.getStatus());
      backstageScaffolderTaskEntity.setTaskCreatedAt(
          DateUtils.parseTimestamp(backstageScaffolderTask.getCreatedAt(), TIMESTAMP_WITH_TIMEZONE));
      backstageScaffolderTaskEntity.setLastHeartbeatAt(
          DateUtils.parseTimestamp(backstageScaffolderTask.getLastHeartbeatAt(), TIMESTAMP_WITH_TIMEZONE));
      backstageScaffolderTaskEntity.setSecrets(backstageScaffolderTask.getSecrets());
      backstageScaffolderTaskEntity.setTaskCreatedBy(backstageScaffolderTaskEntity.getTaskCreatedBy());
      backstageScaffolderTasksEntities.add(backstageScaffolderTaskEntity);
    });
    return backstageScaffolderTasksEntities;
  }
}
