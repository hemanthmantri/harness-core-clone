/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.search;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.search.beans.ArtifactFilter;
import io.harness.ssca.search.entities.Component;
import io.harness.ssca.search.entities.SSCAArtifact;
import io.harness.ssca.search.mapper.ComponentMapper;
import io.harness.ssca.search.mapper.SSCAArtifactMapper;
import io.harness.ssca.search.utils.ElasticSearchUtils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.SSCA)
@Slf4j
public class SearchServiceImpl implements SearchService {
  @Inject private ElasticsearchClient elasticsearchClient;
  @Inject @Named("SSCA") ElasticSearchIndexManager elasticSearchIndexManager;

  @Override
  public Result saveArtifact(ArtifactEntity artifactEntity) {
    String index = elasticSearchIndexManager.getIndex(artifactEntity.getAccountId());
    try {
      IndexRequest<SSCAArtifact> request = IndexRequest.of(i
          -> i.index(index)
                 .id(artifactEntity.getOrchestrationId())
                 .opType(OpType.Create)
                 .routing(artifactEntity.getAccountId())
                 .refresh(Refresh.True)
                 .document(SSCAArtifactMapper.toSSCAArtifact(artifactEntity)));

      IndexResponse response = elasticsearchClient.index(request);
      return response.result();
    } catch (IOException ex) {
      throw new GeneralException("Could not save artifact", ex);
    }
  }

  @Override
  public Result upsertArtifact(ArtifactEntity artifactEntity) {
    String index = elasticSearchIndexManager.getIndex(artifactEntity.getAccountId());
    try {
      UpdateResponse upsertResponse = elasticsearchClient.update(u
          -> u.index(index)
                 .id(artifactEntity.getOrchestrationId())
                 .routing(artifactEntity.getAccountId())
                 .docAsUpsert(true)
                 .doc(SSCAArtifactMapper.toSSCAArtifact(artifactEntity)),
          SSCAArtifact.class);

      return upsertResponse.result();
    } catch (IOException ex) {
      throw new GeneralException("Could not upsert artifact", ex);
    }
  }

  @Override
  public Result updateArtifact(ArtifactEntity artifactEntity) {
    String index = elasticSearchIndexManager.getIndex(artifactEntity.getAccountId());
    try {
      UpdateResponse updateResponse = elasticsearchClient.update(u
          -> u.index(index)
                 .id(artifactEntity.getOrchestrationId())
                 .routing(artifactEntity.getAccountId())
                 .doc(SSCAArtifactMapper.toSSCAArtifact(artifactEntity)),
          SSCAArtifact.class);

      return updateResponse.result();
    } catch (IOException ex) {
      throw new GeneralException("Could not update artifact", ex);
    }
  }

  @Override
  public Result saveComponent(NormalizedSBOMComponentEntity component) {
    String index = elasticSearchIndexManager.getIndex(component.getAccountId());
    try {
      IndexRequest<Component> request = IndexRequest.of(i
          -> i.index(index)
                 .id(component.getUuid())
                 .opType(OpType.Create)
                 .routing(component.getAccountId())
                 .refresh(Refresh.True)
                 .document(ComponentMapper.toComponent(component.getOrchestrationId(), component)));
      IndexResponse response = elasticsearchClient.index(request);
      return response.result();
    } catch (IOException ex) {
      throw new GeneralException("Could not save artifact", ex);
    }
  }

  @Override
  public boolean bulkSaveComponents(String accountId, List<NormalizedSBOMComponentEntity> components) {
    List<NormalizedSBOMComponentEntity> filteredComponents = getFilteredComponents(components);
    if (filteredComponents.isEmpty()) {
      return true;
    }

    String index = elasticSearchIndexManager.getIndex(accountId);
    BulkRequest.Builder builder = new BulkRequest.Builder();

    filteredComponents.forEach(component
        -> builder.operations(op
            -> op.index(i
                -> i.index(index)
                       .routing(accountId)
                       .id(component.getUuid())
                       .document(ComponentMapper.toComponent(component.getOrchestrationId(), component)))));

    try {
      BulkResponse bulkResponse = elasticsearchClient.bulk(builder.build());
      return ElasticSearchUtils.handleBulkErrors(bulkResponse);
    } catch (IOException ex) {
      throw new GeneralException("Could not save components", ex);
    }
  }

  @Override
  public boolean bulkSaveArtifacts(String accountId, List<ArtifactEntity> artifactEntities) {
    List<ArtifactEntity> filteredArtifacts = getFilteredArtifacts(artifactEntities);

    if (filteredArtifacts.isEmpty()) {
      return true;
    }

    String index = elasticSearchIndexManager.getIndex(accountId);
    BulkRequest.Builder builder = new BulkRequest.Builder();

    filteredArtifacts.forEach(artifact
        -> builder.operations(op
            -> op.index(i
                -> i.index(index)
                       .routing(accountId)
                       .id(artifact.getOrchestrationId())
                       .document(SSCAArtifactMapper.toSSCAArtifact(artifact)))));

    try {
      BulkResponse bulkResponse = elasticsearchClient.bulk(builder.build());
      return ElasticSearchUtils.handleBulkErrors(bulkResponse);
    } catch (IOException ex) {
      throw new GeneralException("Could not save artifacts", ex);
    }
  }

  private List<ArtifactEntity> getFilteredArtifacts(List<ArtifactEntity> artifactEntities) {
    return artifactEntities.stream()
        .filter(entity -> !EmptyPredicate.isEmpty(entity.getOrchestrationId()))
        .collect(Collectors.toList());
  }

  private List<NormalizedSBOMComponentEntity> getFilteredComponents(
      List<NormalizedSBOMComponentEntity> normalizedSBOMComponentEntities) {
    return normalizedSBOMComponentEntities.stream()
        .filter(entity -> !EmptyPredicate.isEmpty(entity.getOrchestrationId()))
        .collect(Collectors.toList());
  }

  @Override
  public boolean deleteIndex(String indexName) {
    return elasticSearchIndexManager.deleteIndexByName(indexName);
  }

  public List<String> getOrchestrationIds(
      String accountId, String orgIdentifier, String projectIdentifier, ArtifactFilter filter) {
    List<Hit<SSCAArtifact>> artifacts =
        listArtifacts(accountId, orgIdentifier, projectIdentifier, filter, Pageable.unpaged());

    if (artifacts != null) {
      return artifacts.stream()
          .map(sscaArtifactHit -> sscaArtifactHit.source().getOrchestrationId())
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public List<Hit<SSCAArtifact>> listArtifacts(
      String accountId, String orgIdentifier, String projectIdentifier, ArtifactFilter filter, Pageable pageable) {
    String index = elasticSearchIndexManager.getIndex(accountId);

    // Temporary fix to increase the size of the returned values. This will soon be migrated to a paginated ELK query
    SearchRequest searchRequest = SearchRequest.of(s
        -> s.index(index)
               .query(ArtifactQueryBuilder.getQuery(accountId, orgIdentifier, projectIdentifier, filter))
               .size(10000));

    try {
      return elasticsearchClient.search(searchRequest, SSCAArtifact.class).hits().hits();
    } catch (IOException e) {
      throw new InvalidRequestException("Could not perform this operation", e);
    }
  }
}
