/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.search;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.search.beans.ArtifactFilter;
import io.harness.ssca.search.entities.SSCAArtifact;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.util.List;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.SSCA)
public interface SearchService {
  Result saveArtifact(ArtifactEntity artifactEntity);

  Result updateArtifact(ArtifactEntity artifactEntity);

  Result upsertArtifact(ArtifactEntity artifactEntity);

  Result saveComponent(NormalizedSBOMComponentEntity component);

  boolean bulkSaveComponents(String accountId, List<NormalizedSBOMComponentEntity> components);

  boolean bulkSaveArtifacts(String accountId, List<ArtifactEntity> artifactEntities);

  boolean deleteIndex(String indexName);

  List<String> getOrchestrationIds(
      String accountId, String orgIdentifier, String projectIdentifier, ArtifactFilter filter);

  List<Hit<SSCAArtifact>> listArtifacts(
      String accountId, String orgIdentifier, String projectIdentifier, ArtifactFilter filter, Pageable pageable);
}
