/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.remote.OrganizationMapper.toOrganization;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.data.domain.Pageable.unpaged;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.core.spring.OrganizationRepository;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.helpers.OrganizationInstrumentationHelper;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.cache.Cache;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class OrganizationServiceImplTest extends CategoryTest {
  @Mock private OrganizationRepository organizationRepository;
  @Mock private OutboxService outboxService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private NgUserService ngUserService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ScopeAccessHelper scopeAccessHelper;
  @Mock private OrganizationInstrumentationHelper instrumentationHelper;
  private OrganizationServiceImpl organizationService;
  @Mock private DefaultUserGroupService defaultUserGroupService;
  @Mock private Cache<String, ScopeInfo> scopeInfoCache;
  @Mock private ScopeInfoHelper scopeInfoHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    organizationService = spy(new OrganizationServiceImpl(organizationRepository, outboxService, transactionTemplate,
        ngUserService, accessControlClient, scopeAccessHelper, instrumentationHelper, defaultUserGroupService,
        scopeInfoCache, scopeInfoHelper));
    when(scopeAccessHelper.getPermittedScopes(any())).then(returnsFirstArg());
  }

  private OrganizationDTO createOrganizationDTO(String identifier) {
    return OrganizationDTO.builder().identifier(identifier).name(randomAlphabetic(10)).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateOrganization() {
    String accountIdentifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(randomAlphabetic(10));
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    GlobalContext globalContext = new GlobalContext();
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder()
            .principal(new UserPrincipal("user", "admin@harness.io", "user", accountIdentifier))
            .build();
    globalContext.setGlobalContextRecord(sourcePrincipalContextData);
    GlobalContextManager.set(globalContext);

    when(organizationRepository.save(organization)).thenReturn(organization);
    when(outboxService.save(any())).thenReturn(OutboxEvent.builder().build());
    when(transactionTemplate.execute(any())).thenReturn(organization);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .uniqueId(accountIdentifier)
                              .build();
    Organization createdOrganization = organizationService.create(accountIdentifier, scopeInfo, organizationDTO);
    scopeInfo.setOrgIdentifier(organizationDTO.getIdentifier());
    verify(transactionTemplate, times(1)).execute(any());
    Scope scope = Scope.of(accountIdentifier, organizationDTO.getIdentifier(), null);
    verify(defaultUserGroupService, times(1)).create(scope, emptyList());
    assertEquals(organization, createdOrganization);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateExistentOrganization() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);
    ScopeInfo builtScope = ScopeInfo.builder()
                               .accountIdentifier(accountIdentifier)
                               .scopeType(ScopeLevel.ACCOUNT)
                               .uniqueId(accountIdentifier)
                               .build();

    when(organizationRepository.save(any())).thenReturn(organization);
    when(organizationService.get(accountIdentifier, builtScope, identifier)).thenReturn(Optional.of(organization));

    organizationService.update(accountIdentifier, builtScope, identifier, organizationDTO);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test(expected = JerseyViolationException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateOrganization_IncorrectPayload() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeInfo builtScope = ScopeInfo.builder()
                               .accountIdentifier(accountIdentifier)
                               .scopeType(ScopeLevel.ACCOUNT)
                               .uniqueId(accountIdentifier)
                               .build();
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    organizationDTO.setName("");
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);
    organization.setName(randomAlphabetic(10));
    when(organizationService.get(accountIdentifier, builtScope, identifier)).thenReturn(Optional.of(organization));

    organizationService.update(accountIdentifier, builtScope, identifier, organizationDTO);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateNonExistentOrganization() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);

    when(organizationService.get(accountIdentifier, identifier)).thenReturn(Optional.empty());

    Organization updatedOrganization = organizationService.update(accountIdentifier,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .uniqueId(accountIdentifier)
            .build(),
        identifier, organizationDTO);

    assertNull(updatedOrganization);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void update_onlyIdentifierChanged_noop() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = "identifier";
    ScopeInfo builtScope = ScopeInfo.builder()
                               .accountIdentifier(accountIdentifier)
                               .scopeType(ScopeLevel.ACCOUNT)
                               .uniqueId(accountIdentifier)
                               .build();
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);

    when(organizationService.get(accountIdentifier, builtScope, identifier.toUpperCase()))
        .thenReturn(Optional.of(organization));
    when(transactionTemplate.execute(any())).thenReturn(organization);

    organizationDTO.setIdentifier(identifier.toUpperCase());
    Organization updatedOrganization =
        organizationService.update(accountIdentifier, builtScope, identifier.toUpperCase(), organizationDTO);

    assertNotNull(updatedOrganization);
    assertThat(updatedOrganization.getIdentifier()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void update_identifierAndSomeOtherFieldChanged_onlyOtherFieldIsUpdated() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = "identifier";
    ScopeInfo builtScope = ScopeInfo.builder()
                               .accountIdentifier(accountIdentifier)
                               .scopeType(ScopeLevel.ACCOUNT)
                               .uniqueId(accountIdentifier)
                               .build();
    Organization existingOrganization = Organization.builder()
                                            .accountIdentifier(accountIdentifier)
                                            .identifier(identifier)
                                            .name("test")
                                            .description("desc")
                                            .build();

    when(organizationService.get(accountIdentifier, builtScope, identifier.toUpperCase()))
        .thenReturn(Optional.of(existingOrganization));

    OrganizationDTO updateDTO = OrganizationDTO.builder()
                                    .name("updatedTest")
                                    .description("updatedDesc")
                                    .identifier(identifier.toUpperCase())
                                    .build();
    Organization expectedUpdatedOrg = existingOrganization;
    expectedUpdatedOrg.setDescription("updatedDesc");
    expectedUpdatedOrg.setName("updatedTest");
    when(transactionTemplate.execute(any())).thenReturn(expectedUpdatedOrg);
    Organization updatedOrganization =
        organizationService.update(accountIdentifier, builtScope, identifier.toUpperCase(), updateDTO);

    assertNotNull(updatedOrganization);
    assertThat(updatedOrganization.getIdentifier()).isEqualTo(identifier);
    assertThat(updatedOrganization.getName()).isEqualTo(expectedUpdatedOrg.getName());
    assertThat(updatedOrganization.getDescription()).isEqualTo(expectedUpdatedOrg.getDescription());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListOrganization() {
    String accountIdentifier = randomAlphabetic(10);
    String searchTerm = randomAlphabetic(5);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(organizationRepository.findAllOrgs(any(Criteria.class))).thenReturn(Collections.emptyList());
    when(organizationRepository.findAll(any(Criteria.class), any(Pageable.class), anyBoolean()))
        .thenReturn(getPage(emptyList(), 0));

    Page<Organization> organizationPage = organizationService.listPermittedOrgs(accountIdentifier,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .uniqueId(accountIdentifier)
            .build(),
        unpaged(), OrganizationFilterDTO.builder().searchTerm(searchTerm).build());

    verify(organizationRepository, times(1)).findAllOrgs(criteriaArgumentCaptor.capture());

    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document criteriaObject = criteria.getCriteriaObject();

    assertEquals(4, criteriaObject.size());
    assertEquals(accountIdentifier, criteriaObject.get(OrganizationKeys.accountIdentifier));
    assertTrue(criteriaObject.containsKey(OrganizationKeys.deleted));

    assertEquals(0, organizationPage.getTotalElements());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testHardDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Long version = 0L;
    OrganizationDTO organizationDTO = createOrganizationDTO(identifier);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    organization.setIdentifier(identifier);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(organizationRepository.hardDelete(any(), any(), any(), any())).thenReturn(organization);

    organizationService.delete(accountIdentifier,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .uniqueId(accountIdentifier)
            .build(),
        identifier, version);
    verify(organizationRepository, times(1)).hardDelete(any(), any(), argumentCaptor.capture(), any());
    assertEquals(identifier, argumentCaptor.getValue());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(any());
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testHardDeleteInvalidIdentifier() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Long version = 0L;

    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(organizationRepository.hardDelete(any(), any(), any(), any())).thenReturn(null);

    organizationService.delete(accountIdentifier,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .uniqueId(accountIdentifier)
            .build(),
        identifier, version);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetPermittedOrganizationsWhenOrgIdentifier() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    Scope o1 = Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build();
    List<Scope> organizations = Collections.singletonList(o1);

    Set<String> permittedOrganizations = organizationService.getPermittedOrganizations(accountIdentifier,
        ScopeInfo.builder()
            .accountIdentifier(accountIdentifier)
            .scopeType(ScopeLevel.ACCOUNT)
            .uniqueId(accountIdentifier)
            .build(),
        orgIdentifier);
    assertEquals(permittedOrganizations.size(), 1);
    assertTrue(permittedOrganizations.contains(orgIdentifier));
    verify(scopeAccessHelper, times(1)).getPermittedScopes(organizations);
    verifyNoMoreInteractions(scopeAccessHelper);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetPermittedOrganizationsWhenNoOrgIdentifier() {
    String accountIdentifier = randomAlphabetic(10);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .uniqueId(accountIdentifier)
                              .build();
    Scope o1 = Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier("O1").build();
    Scope o2 = Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier("O2").build();
    List<Scope> organizations = new ArrayList<>(Arrays.asList(o1, o2));
    Criteria orgCriteria = Criteria.where(OrganizationKeys.accountIdentifier)
                               .is(accountIdentifier)
                               .and(OrganizationKeys.parentUniqueId)
                               .is(scopeInfo.getUniqueId())
                               .and(OrganizationKeys.deleted)
                               .ne(Boolean.TRUE);
    when(scopeAccessHelper.getPermittedScopes(organizations)).thenReturn(Collections.singletonList(o1));
    when(organizationRepository.findAllOrgs(orgCriteria)).thenReturn(organizations);
    Set<String> permittedOrganizations =
        organizationService.getPermittedOrganizations(accountIdentifier, scopeInfo, null);
    assertEquals(permittedOrganizations.size(), 1);
    assertTrue(permittedOrganizations.contains("O1"));
    verify(organizationRepository, times(1)).findAllOrgs(orgCriteria);
    verifyNoMoreInteractions(organizationRepository);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldGetOrganizationIdentifierCaseInsensitive() {
    String accountIdentifier = "accountIdentifier";
    String organizationIdentifier = "organizationIdentifier";
    organizationService.get(accountIdentifier, organizationIdentifier);
    verify(organizationRepository, times(1))
        .findByAccountIdentifierAndIdentifierIgnoreCaseAndDeletedNot(accountIdentifier, organizationIdentifier, true);
  }
}
