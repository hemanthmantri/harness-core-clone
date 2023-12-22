/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.api;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.triggers.v1.NGTriggerApiUtils;
import io.harness.spec.server.pipeline.v1.TriggersApi;
import io.harness.spec.server.pipeline.v1.model.TriggerRequestBody;

import com.google.inject.Inject;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class TriggersApiImpl implements TriggersApi {
  private final NGTriggerService ngTriggerService;
  private final AccessControlClient accessControlClient;
  private final NGTriggerEventsService ngTriggerEventsService;
  private final NGTriggerApiUtils ngTriggerApiUtils;

  @Override
  public Response createTrigger(@Valid TriggerRequestBody body, String org, String project, String pipeline,
      Boolean ignoreError, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_EXECUTE);
    NGTriggerEntity createdEntity;
    try {
      TriggerDetails triggerDetails = ngTriggerApiUtils.toTriggerDetails(harnessAccount, org, project, body, pipeline);
      ngTriggerService.validateTriggerConfig(triggerDetails);
      if (ignoreError != null && ignoreError) {
        createdEntity = ngTriggerService.create(triggerDetails.getNgTriggerEntity());
      } else {
        ngTriggerService.validatePipelineRef(triggerDetails);
        createdEntity = ngTriggerService.create(triggerDetails.getNgTriggerEntity());
      }
      return Response.ok().entity(ngTriggerApiUtils.toResponseDTO(createdEntity)).build();
    } catch (Exception e) {
      throw new InvalidRequestException("Failed while Saving Trigger: " + e.getMessage());
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response getTrigger(@OrgIdentifier String org, @ProjectIdentifier String project, String pipeline,
      String trigger, @AccountIdentifier String harnessAccount) {
    Optional<NGTriggerEntity> ngTriggerEntity =
        ngTriggerService.get(harnessAccount, org, project, pipeline, trigger, false);

    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", trigger));
    }
    return Response.ok().entity(ngTriggerApiUtils.toGetResponseDTO(ngTriggerEntity.get())).build();
  }

  @Override
  public Response updateTrigger(@Valid TriggerRequestBody body, String org, String project, String pipeline,
      String trigger, Boolean ignoreError, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of("PIPELINE", pipeline), PipelineRbacPermissions.PIPELINE_EXECUTE);
    Optional<NGTriggerEntity> ngTriggerEntity =
        ngTriggerService.get(harnessAccount, org, project, pipeline, trigger, false);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", trigger));
    }

    try {
      TriggerDetails triggerDetails = ngTriggerApiUtils.toTriggerDetails(harnessAccount, org, project, body, pipeline);
      triggerDetails = ngTriggerService.fetchTriggerEntityV1(harnessAccount, org, project, pipeline, trigger,
          triggerDetails.getNgTriggerConfigV2(), triggerDetails.getNgTriggerEntity());

      ngTriggerService.validateTriggerConfig(triggerDetails);
      NGTriggerEntity updatedEntity;
      if (ignoreError != null && ignoreError) {
        updatedEntity = ngTriggerService.update(triggerDetails.getNgTriggerEntity(), ngTriggerEntity.get());
      } else {
        ngTriggerService.validatePipelineRef(triggerDetails);
        updatedEntity = ngTriggerService.update(triggerDetails.getNgTriggerEntity(), ngTriggerEntity.get());
      }
      return Response.ok().entity(ngTriggerApiUtils.toResponseDTO(updatedEntity)).build();
    } catch (Exception e) {
      throw new InvalidRequestException("Failed while updating Trigger: " + e.getMessage());
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response deleteTrigger(@OrgIdentifier String org, @ProjectIdentifier String project, String pipeline,
      String trigger, @AccountIdentifier String harnessAccount) {
    boolean triggerDeleted = ngTriggerService.delete(harnessAccount, org, project, pipeline, trigger, null);
    if (triggerDeleted) {
      ngTriggerEventsService.deleteTriggerEventHistory(harnessAccount, org, project, pipeline, trigger);
    }
    return Response.status(204).build();
  }
}
