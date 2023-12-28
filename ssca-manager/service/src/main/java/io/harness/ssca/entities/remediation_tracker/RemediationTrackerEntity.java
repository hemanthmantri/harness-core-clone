/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ssca.entities.remediation_tracker;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.SSCA)
@FieldNameConstants(innerTypeName = "RemediationTrackerEntityKeys")
@Entity(value = "remediationTrackers", noClassnameStored = true)
@Document("remediationTrackers")
@TypeAlias("remediationTrackers")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.SSCA)
public class RemediationTrackerEntity implements UuidAware, PersistentRegularIterable, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_query_idx")
                 .unique(true)
                 .field(RemediationTrackerEntityKeys.accountIdentifier)
                 .field(RemediationTrackerEntityKeys.orgIdentifier)
                 .field(RemediationTrackerEntityKeys.projectIdentifier)
                 .field(RemediationTrackerEntityKeys.uuid)
                 .build())
        .build();
  }
  @NonFinal @Id String uuid; // uuid of the tracker entity.
  @NotNull String accountIdentifier;
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;
  long startTimeMilli;
  Long endTimeMilli;
  ContactInfo contactInfo;
  VulnerabilityInfo vulnerabilityInfo;
  @NotNull RemediationCondition condition;
  String ticketId;
  RemediationStatus status;
  Map<String, ArtifactInfo> artifactInfos;
  String latestTagWithFix;
  DeploymentsCount deploymentsCount;
  @FdIndex long nextIteration;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  String comments;
  boolean closedManually;
  String closedBy;
  Long targetEndDateEpochDay;
  String createdBy;
  String lastUpdatedBy;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (RemediationTrackerEntityKeys.nextIteration.equals(fieldName)) {
      return this.nextIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public ZoneOffset getZoneOffset() {
    return ZoneOffset.UTC; // hardcoding it to UTC for now. We need to ask it from user.
  }
  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (RemediationTrackerEntityKeys.nextIteration.equals(fieldName)) {
      this.nextIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public String getAccountId() {
    return this.accountIdentifier;
  }
}
