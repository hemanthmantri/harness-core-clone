/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.resources;

import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAPIL;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NotificationRuleResourceTest extends CvNextGenTestBase {
  @Inject private NotificationRuleService notificationRuleService;
  @Inject private Injector injector;
  @Inject private MetricPackService metricPackService;
  @Inject private MonitoredServiceService monitoredServiceService;
  private BuilderFactory builderFactory;
  private MonitoredServiceDTO monitoredServiceDTO;
  private static NotificationRuleResource notificationRuleResource = new NotificationRuleResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(notificationRuleResource).build();

  @Before
  public void setup() {
    injector.injectMembers(notificationRuleResource);
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveNotificationRuleData() throws IOException {
    String notificationYaml = getYAML("notification/notification-rule.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveNotificationRuleData_withDuplicateEntity() throws IOException {
    String notificationYaml = getYAML("notification/notification-rule.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(200);

    response = RESOURCES.client()
                   .target("http://localhost:9998/notification-rule/")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class))
        .contains(
            "\"ERROR\",\"message\":\"io.harness.exception.DuplicateFieldException: NotificationRule with identifier rule, accountId "
            + builderFactory.getContext().getAccountId() + ", orgIdentifier "
            + builderFactory.getContext().getOrgIdentifier() + " and projectIdentifier "
            + builderFactory.getContext().getProjectIdentifier() + " is already present\"");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testSaveNotificationRuleData_withDuplicateEntity_AccountLevel() throws IOException {
    String notificationYaml = getYAML("notification/notification-rule-account-level.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(200);

    response = RESOURCES.client()
                   .target("http://localhost:9998/notification-rule/")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class))
        .contains(
            "\"ERROR\",\"message\":\"io.harness.exception.DuplicateFieldException: NotificationRule with identifier rule, accountId "
            + builderFactory.getContext().getAccountId() + " is already present\"");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveNotificationRuleData_withIncorrectYAML_withoutConditions() throws IOException {
    String notificationYaml = getYAML("notification/notification-rule-invalid.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    ErrorDTO errorDTO = response.readEntity(new GenericType<>() {});
    assertThat(errorDTO.getMessage())
        .isEqualTo("saveNotificationRuleData.notificationRuleDTO.conditions: must not be null");
    assertThat(errorDTO.getResponseMessages().get(0).getMessage()).isEqualTo("conditions : must not be null");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveNotificationRuleData_withIncorrectYAML_withoutNotificationMethod() throws IOException {
    String notificationYaml = getYAML("notification/notification-rule-invalid-2.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    ErrorDTO errorDTO = response.readEntity(new GenericType<>() {});
    assertThat(errorDTO.getResponseMessages()).hasSize(2);
    List<String> list = asList("notificationMethod : must not be null", "conditions : must not be null");
    assertThat(list).contains(errorDTO.getResponseMessages().get(0).getMessage());
    assertThat(list).contains(errorDTO.getResponseMessages().get(1).getMessage());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetNotificationRules() throws IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/notification-rule/")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("pageNumber", 0)
                              .queryParam("pageSize", 10);

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":1");
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetNotificationRules_withIdentifiers() throws IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule2");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setIdentifier("rule3");
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/notification-rule/")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("notificationRuleIdentifiers", "rule2")
                              .queryParam("notificationRuleIdentifiers", "rule3")
                              .queryParam("pageNumber", 0)
                              .queryParam("pageSize", 10);

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":2");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveNotificationRuleData_withMonitoredService() throws IOException {
    String notificationYaml = getYAML("notification/notification-rule-monitored-service.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testDeleteNotificationRuleData() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    monitoredServiceDTO.setName("MSName");
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleDTO.getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/"
                                + "rule")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .delete();
    assertThat(response.readEntity(String.class))
        .contains(
            "\"message\":\"java.lang.IllegalArgumentException: Deleting notification rule is used in Monitored Services, "
            + "Please delete the notification rule inside Monitored Services before deleting notification rule. Monitored Services : MSName");
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetNotificationRuleData() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/"
                                + "rule")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    RestResponse<NotificationRuleResponse> restResponse = response.readEntity(new GenericType<>() {});
    assertThat(restResponse.getResource().getNotificationRule()).isEqualTo(notificationRuleDTO);
  }

  private String getYAML(String filePath) throws IOException {
    return getYAML(filePath, monitoredServiceDTO.getIdentifier());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveNotificationRuleData_withSLO_withInvalidThresholds() throws IOException {
    String notificationYaml = getYAML("notification/notification-rule-slo-invalid-threshold.yaml");
    notificationYaml = notificationYaml.replace("$RemainingPercentageThreshold", "-10");
    notificationYaml = notificationYaml.replace("$RemainingMinutesThreshold", "20");
    notificationYaml = notificationYaml.replace("$BurnRateThreshold", "5");
    notificationYaml = notificationYaml.replace("$Duration", "10m");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    ErrorDTO errorDTO = response.readEntity(new GenericType<>() {});
    assertThat(errorDTO.getResponseMessages().get(0).getMessage())
        .isEqualTo("threshold : must be greater than or equal to 0");
    notificationYaml = getYAML("notification/notification-rule-slo-invalid-threshold.yaml");
    notificationYaml = notificationYaml.replace("$RemainingPercentageThreshold", "10");
    notificationYaml = notificationYaml.replace("$RemainingMinutesThreshold", "20");
    notificationYaml = notificationYaml.replace("$BurnRateThreshold", "-5");
    notificationYaml = notificationYaml.replace("$Duration", "10m");
    response = RESOURCES.client()
                   .target("http://localhost:9998/notification-rule/")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    errorDTO = response.readEntity(new GenericType<>() {});
    assertThat(errorDTO.getResponseMessages().get(0).getMessage())
        .isEqualTo("threshold : must be greater than or equal to 0");
    notificationYaml = getYAML("notification/notification-rule-slo-invalid-threshold.yaml");
    notificationYaml = notificationYaml.replace("$RemainingPercentageThreshold", "10");
    notificationYaml = notificationYaml.replace("$RemainingMinutesThreshold", "20");
    notificationYaml = notificationYaml.replace("$BurnRateThreshold", "5");
    notificationYaml = notificationYaml.replace("$Duration", "-10m");
    response = RESOURCES.client()
                   .target("http://localhost:9998/notification-rule/")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(500);
    errorDTO = response.readEntity(new GenericType<>() {});
    assertThat(errorDTO.getResponseMessages().get(0).getMessage())
        .isEqualTo("java.lang.IllegalArgumentException: duration cannot be a negative value");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveNotificationRuleData_withMonitoredService_withInvalidThresholds() throws IOException {
    String notificationYaml = getYAML("notification/notification-rule-monitored-service-invalid-threshold.yaml");
    notificationYaml = notificationYaml.replace("$ChangeImpactThreshold", "-10");
    notificationYaml = notificationYaml.replace("$ChangeImpactDuration", "20m");
    notificationYaml = notificationYaml.replace("$HealthScoreThreshold", "10");
    notificationYaml = notificationYaml.replace("$HealthScoreDuration", "20m");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/notification-rule/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    ErrorDTO errorDTO = response.readEntity(new GenericType<>() {});
    assertThat(errorDTO.getResponseMessages().get(0).getMessage())
        .isEqualTo("threshold : must be greater than or equal to 0");
    notificationYaml = getYAML("notification/notification-rule-monitored-service-invalid-threshold.yaml");
    notificationYaml = notificationYaml.replace("$ChangeImpactThreshold", "10");
    notificationYaml = notificationYaml.replace("$ChangeImpactDuration", "20m");
    notificationYaml = notificationYaml.replace("$HealthScoreThreshold", "110");
    notificationYaml = notificationYaml.replace("$HealthScoreDuration", "20m");
    response = RESOURCES.client()
                   .target("http://localhost:9998/notification-rule/")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    errorDTO = response.readEntity(new GenericType<>() {});
    assertThat(errorDTO.getResponseMessages().get(0).getMessage())
        .isEqualTo("threshold : must be less than or equal to 100");

    notificationYaml = getYAML("notification/notification-rule-monitored-service-invalid-threshold.yaml");
    notificationYaml = notificationYaml.replace("$ChangeImpactThreshold", "10");
    notificationYaml = notificationYaml.replace("$ChangeImpactDuration", "20m");
    notificationYaml = notificationYaml.replace("$HealthScoreThreshold", "10");
    notificationYaml = notificationYaml.replace("$HealthScoreDuration", "-20m");
    response = RESOURCES.client()
                   .target("http://localhost:9998/notification-rule/")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .post(Entity.json(convertToJson(notificationYaml)));
    assertThat(response.getStatus()).isEqualTo(500);
    errorDTO = response.readEntity(new GenericType<>() {});
    assertThat(errorDTO.getResponseMessages().get(0).getMessage())
        .isEqualTo("java.lang.IllegalArgumentException: duration cannot be a negative value");
  }

  private String getYAML(String filePath, String monitoredServiceIdentifier) throws IOException {
    String sloYaml = getResource(filePath);
    sloYaml = sloYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
    sloYaml = sloYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
    sloYaml = sloYaml.replace("$monitoredServiceRef", monitoredServiceIdentifier);
    sloYaml = sloYaml.replace(
        "$healthSourceRef", monitoredServiceDTO.getSources().getHealthSources().iterator().next().getIdentifier());
    return sloYaml;
  }
}
