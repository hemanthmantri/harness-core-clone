/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.ScopeInfoFactory.SCOPE_INFO_CONTEXT_PROPERTY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfo.ScopeInfoBuilder;
import io.harness.beans.ScopeInfoContext;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.beans.ScopeLevel;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Priority;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Priority(Priorities.USER)
@Slf4j
@Provider
public class ScopeInfoFilter implements ContainerRequestFilter, ContainerResponseFilter {
  private static final String SCOPE_INFO_FILTER_LOG = "SCOPE_INFO_FILTER:";
  public static final Set<String> matchingPathRequests =
      Set.of("/organizations", "/aggregate/organizations", "/projects", "/aggregate/projects");

  private final ScopeInfoClient scopeInfoClient;
  @Context private ResourceInfo resourceInfo;

  public ScopeInfoFilter(@Named("PRIVILEGED") ScopeInfoClient scopeInfoClient) {
    this.scopeInfoClient = scopeInfoClient;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (isFilterApplicableOnRequest(requestContext)) {
      String existingAccountIdentifier =
          requestContext.getHeaderString(ScopeInfoContext.SCOPE_INFO_ACCOUNT_CONTEXT_PROPERTY);
      String existingOrgIdentifier = requestContext.getHeaderString(ScopeInfoContext.SCOPE_INFO_ORG_CONTEXT_PROPERTY);
      String existingProjectIdentifier =
          requestContext.getHeaderString(ScopeInfoContext.SCOPE_INFO_PROJECT_CONTEXT_PROPERTY);
      String existingUniqueId = requestContext.getHeaderString(ScopeInfoContext.SCOPE_INFO_UNIQUE_ID_CONTEXT_PROPERTY);
      String existingScopeType =
          requestContext.getHeaderString(ScopeInfoContext.SCOPE_INFO_SCOPE_TYPE_CONTEXT_PROPERTY);

      ScopeInfoBuilder existingScopeInfoBuilder = ScopeInfo.builder();
      if (isNotEmpty(existingAccountIdentifier) && isNotEmpty(existingScopeType) && isNotEmpty(existingUniqueId)) {
        existingScopeInfoBuilder.accountIdentifier(existingAccountIdentifier).build();
        existingScopeInfoBuilder.orgIdentifier(existingOrgIdentifier);
        existingScopeInfoBuilder.projectIdentifier(existingProjectIdentifier);
        existingScopeInfoBuilder.scopeType(ScopeLevel.valueOf(existingScopeType));
        existingScopeInfoBuilder.uniqueId(existingUniqueId);
      }
      ScopeInfo existingScopeInfo = existingScopeInfoBuilder.build();

      Optional<String> accountIdentifierOptional = getAccountIdentifierFrom(requestContext);
      if (accountIdentifierOptional.isEmpty() || accountIdentifierOptional.get().isEmpty()) {
        // there can be cases when account identifier may actually be not present in API,
        // they would continue to be handled by upstream as is
        log.warn(format("%s No accountId present in the request", SCOPE_INFO_FILTER_LOG));
        return;
      }

      if (!isScopeInfoResolutionExemptedRequest(resourceInfo, requestContext)) {
        String accountIdentifier = accountIdentifierOptional.get();
        /*
          We first try to infer scope from api path signature and path param
          - If last OR second-last segment of path is 'org' OR 'organizations' then we will consider it as ACCOUNT scope
            For example below paths are ACCOUNT scope
              /organizations
              /organizations/{identifier}
              /organizations/all-organizations
              /aggregate/organizations/{identifier}
              /aggregate/organizations
              /orgs
              /orgs/{org}

          - If last OR second-last segment of path is 'projects' then we will consider it as ORGANIZATION scope
            For example below paths are ORGANIZATION scope
              /aggregate/projects/{identifier}
              /aggregate/projects
              /projects
              /projects/{identifier}
              /projects/all-projects
              /projects/project-count
              /orgs/{org}/projects
              /orgs/{org}/projects/{project}

          - For api paths which follows below pattern we will consider it as PROJECT scope
            For example below paths are PROJECT scope
              /orgs/{org}/projects/{project}/secrets
              /orgs/{org}/projects/{project}/secrets/{secret}

        If we are not able to figure out scope from above path patterns then we will look for query params
        It supports below query params
          - ACCOUNT scope
              /secrets?accountIdentifier=someAccount&orgIdentifier=someOrg

          - ORGANIZATION scope
              /secrets?accountIdentifier=someAccount&orgIdentifier=someOrg

          - PROJECT scope
              /secrets?accountIdentifier=someAccount&orgIdentifier=someOrg&projectIdentifier=someProject


        For api paths which do not fall in any of above patterns, api owners need to manually resolve scope in that
        case. They also need to mark there api method as @ScopeInfoResolutionExemptedApi to avoid automatic scope
        resolution from path OR query param. For example below apis in ng-manager can have any scope
        ACCOUNT/ORGANIZATION/PROJECT
              - /artifacts/azureartifacts/projects
              - /artifacts/azureartifacts/v2/projects
              - /gcp/project
              - /jira/projects

        This deviates from supported api path patterns. So automatic scope resolution should be avoided in
        this case by marking these api methods as @ScopeInfoResolutionExemptedApi
        */
        String orgIdentifier = getOrgIdentifierFrom(requestContext).orElse(null);
        String projectIdentifier =
            isNotEmpty(orgIdentifier) ? getProjectIdentifierFrom(requestContext).orElse(null) : null;

        if (Objects.equals(accountIdentifier, existingScopeInfo.getAccountIdentifier())
            && Objects.equals(orgIdentifier, existingScopeInfo.getOrgIdentifier())
            && Objects.equals(projectIdentifier, existingScopeInfo.getProjectIdentifier())) {
          requestContext.setProperty(SCOPE_INFO_CONTEXT_PROPERTY, existingScopeInfo);
          ScopeInfoContext.setScopeInfo(existingScopeInfo);
          return;
        }

        Optional<ScopeInfo> optionalScopeInfo =
            NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
        if (optionalScopeInfo.isEmpty()) {
          String errorMsg = null;
          if (isEmpty(orgIdentifier) && isEmpty(projectIdentifier)) {
            errorMsg = format("Account with identifier [%s] not found", accountIdentifier);
          } else if (isEmpty(projectIdentifier)) {
            errorMsg = format("Organization with identifier [%s] not found", orgIdentifier);
          } else {
            errorMsg = format("Project with identifier [%s] not found", projectIdentifier);
          }
          throw new NotFoundException(errorMsg);
        }
        ScopeInfo scopeInfo = ScopeInfo.builder()
                                  .accountIdentifier(accountIdentifier)
                                  .orgIdentifier(optionalScopeInfo.get().getOrgIdentifier())
                                  .projectIdentifier(optionalScopeInfo.get().getProjectIdentifier())
                                  .scopeType(optionalScopeInfo.get().getScopeType())
                                  .uniqueId(optionalScopeInfo.get().getUniqueId())
                                  .build();

        requestContext.setProperty(SCOPE_INFO_CONTEXT_PROPERTY, scopeInfo);
        ScopeInfoContext.setScopeInfo(scopeInfo);
      }
    }
  }

  private Optional<String> getAccountIdentifierFrom(ContainerRequestContext containerRequestContext) {
    String accountIdentifier = containerRequestContext.getHeaderString(ACCOUNT_HEADER);

    if (isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    if (isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    if (isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT);
    }
    final String accountIdStringConstant = "accountId";
    if (isEmpty(accountIdentifier)) {
      accountIdentifier = containerRequestContext.getUriInfo().getQueryParameters().getFirst(accountIdStringConstant);
    }
    if (isEmpty(accountIdentifier)) {
      accountIdentifier = containerRequestContext.getUriInfo().getPathParameters().getFirst(accountIdStringConstant);
    }
    return isEmpty(accountIdentifier) ? Optional.empty() : Optional.of(accountIdentifier);
  }

  @VisibleForTesting
  protected Optional<String> getOrgIdentifierFrom(ContainerRequestContext containerRequestContext) {
    String orgIdentifier =
        containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.ORG);

    List<PathSegment> pathSegments = containerRequestContext.getUriInfo().getPathSegments();
    if (!pathSegments.isEmpty()) {
      int segmentSize = pathSegments.size();
      String lastSegment = (segmentSize > 0) ? pathSegments.get(segmentSize - 1).getPath() : null;
      String secondLastSegment = (segmentSize > 1) ? pathSegments.get(segmentSize - 2).getPath() : null;
      if (Objects.equals(lastSegment, NGCommonEntityConstants.ORGS)
          || Objects.equals(secondLastSegment, NGCommonEntityConstants.ORGS)
          || Objects.equals(lastSegment, NGCommonEntityConstants.ORGANIZATIONS)
          || Objects.equals(secondLastSegment, NGCommonEntityConstants.ORGANIZATIONS)) {
        return Optional.empty();
      }
    }
    if (isEmpty(orgIdentifier)) {
      orgIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ORG_KEY);
    }
    return isEmpty(orgIdentifier) ? Optional.empty() : Optional.of(orgIdentifier);
  }

  @VisibleForTesting
  protected Optional<String> getProjectIdentifierFrom(ContainerRequestContext containerRequestContext) {
    String projectIdentifier =
        containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.PROJECT);

    List<PathSegment> pathSegments = containerRequestContext.getUriInfo().getPathSegments();
    if (!pathSegments.isEmpty()) {
      int segmentSize = pathSegments.size();
      String lastSegment = (segmentSize > 0) ? pathSegments.get(segmentSize - 1).getPath() : null;
      String secondLastSegment = (segmentSize > 1) ? pathSegments.get(segmentSize - 2).getPath() : null;
      if (Objects.equals(lastSegment, NGCommonEntityConstants.PROJECTS)
          || Objects.equals(secondLastSegment, NGCommonEntityConstants.PROJECTS)) {
        return Optional.empty();
      }
    }
    if (isEmpty(projectIdentifier)) {
      projectIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.PROJECT_KEY);
    }
    return isEmpty(projectIdentifier) ? Optional.empty() : Optional.of(projectIdentifier);
  }

  private boolean isScopeInfoResolutionExemptedRequest(
      ResourceInfo requestResourceInfo, ContainerRequestContext requestContext) {
    return ScopeInfoClient.SCOPE_INFO.equals(requestContext.getUriInfo().getPath());
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext)
      throws IOException {
    ScopeInfoContext.clearScopeInfo();
  }

  private boolean isFilterApplicableOnRequest(ContainerRequestContext requestContext) {
    return requestContext != null && requestContext.getUriInfo() != null
        && requestContext.getUriInfo().getRequestUri() != null
        && requestContext.getUriInfo().getRequestUri().getPath() != null
        && !resourceInfo.getResourceMethod().isAnnotationPresent(ScopeInfoResolutionExemptedApi.class)
        && matchingPathRequests.stream().anyMatch(
            requestContext.getUriInfo().getRequestUri().getPath().toLowerCase()::startsWith);
  }
}