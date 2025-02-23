/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ANKUSH;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.RICHA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.MailTaskParams;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.NotificationRequest;
import io.harness.notification.SmtpConfig;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.SmtpConfigResponse;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.notification.remote.dto.EmailSettingDTO;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.senders.MailSenderImpl;
import io.harness.notification.service.MailServiceImpl.EmailTemplate;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.notification.utils.NotificationSettingsHelper;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.userng.remote.UserNGClient;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(PL)
public class MailServiceImplTest extends CategoryTest {
  private static final String TEST_NOTIFICATION_TEMPLATE_CONTENT = "This is a test notification";
  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationTemplateService notificationTemplateService;
  @Mock private YamlUtils yamlUtils;
  @Mock private SmtpConfig smtpConfigDefault;
  @Mock private MailSenderImpl mailSender;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private UserNGClient userNGClient;
  @Mock private Call<ResponseDTO<Boolean>> responseTrue;
  @Mock private Call<ResponseDTO<Boolean>> responseFalse;
  @Mock private NotificationSettingsHelper notificationSettingsHelper;
  private MockedStatic<NGRestUtils> restUtilsMockedStatic;
  private MailServiceImpl mailService;
  private String accountId = "accountId";
  private String mailTemplateName = "email_test";
  private String emailAdress = "email@harness.io";
  private String id = "id";
  private EmailTemplate emailTemplate = new EmailTemplate();
  private static final String VALID_EMAIL_1 = "validEmail1@harness.io";
  private static final String VALID_EMAIL_2 = "validEmail2@harness.io";
  private static final String INVALID_EMAIL_1 = "invalidEmail1@harness.io";
  private static final String INVALID_EMAIL_2 = "invalidEmail2@harness.io";
  private static final List<String> validInvalidPair1 = Arrays.asList(VALID_EMAIL_1, INVALID_EMAIL_1);
  private static final List<String> validInvalidPair2 = Arrays.asList(VALID_EMAIL_2, INVALID_EMAIL_2);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mailService = new MailServiceImpl(notificationSettingsService, notificationTemplateService, yamlUtils,
        smtpConfigDefault, mailSender, delegateGrpcClientWrapper, notificationSettingsHelper, userNGClient);
    emailTemplate.setBody("this is test mail");
    emailTemplate.setSubject("test notification");
    restUtilsMockedStatic = mockStatic(NGRestUtils.class);

    when(NGRestUtils.getResponse(responseTrue)).thenReturn(true);
    when(NGRestUtils.getResponse(responseFalse)).thenReturn(false);
    when(userNGClient.isEmailIdInAccount("email@harness.io", accountId)).thenReturn(responseTrue);
    when(userNGClient.isEmailIdInAccount(VALID_EMAIL_1, accountId)).thenReturn(responseTrue);
    when(userNGClient.isEmailIdInAccount(VALID_EMAIL_2, accountId)).thenReturn(responseTrue);
    when(userNGClient.isEmailIdInAccount(INVALID_EMAIL_1, accountId)).thenReturn(responseFalse);
    when(userNGClient.isEmailIdInAccount(INVALID_EMAIL_2, accountId)).thenReturn(responseFalse);
  }

  @After
  public void cleanup() {
    restUtilsMockedStatic.close();
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();
    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();
    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = {ANKUSH, ASHISHSANODIA})
  @Category(UnitTests.class)
  public void sendNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setEmail(NotificationRequest.Email.newBuilder()
                                                                .setTemplateId(mailTemplateName)
                                                                .addAllEmailIds(Collections.EMPTY_LIST)
                                                                .build())
                                                  .build();
    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithoutTemplateId_WithCustomContent() {
    NotificationRequest.Email emailBuilder = NotificationRequest.Email.newBuilder()
                                                 .addAllEmailIds(Collections.singletonList(emailAdress))
                                                 .addAllCcEmailIds(Collections.singletonList(VALID_EMAIL_1))
                                                 .setSubject("some-subject")
                                                 .setBody("some-body-content")
                                                 .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setEmail(emailBuilder).build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId))).thenReturn(new SmtpConfigResponse());

    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    verify(mailSender, times(1))
        .send(any(), eq(emailBuilder.getCcEmailIdsList()), eq(emailBuilder.getSubject()), eq(emailBuilder.getBody()),
            any(), any());
    assertEquals(notificationProcessingResponse, notificationExpectedResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = {ANKUSH, ASHISHSANODIA})
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithoutDelegate() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setEmail(NotificationRequest.Email.newBuilder()
                                                                .setTemplateId(mailTemplateName)
                                                                .addAllEmailIds(Collections.singletonList(emailAdress))
                                                                .build())
                                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId))).thenReturn(new SmtpConfigResponse());

    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);

    assertTrue(notificationProcessingResponse.equals(notificationExpectedResponse));
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase_WithDelegate() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setEmail(NotificationRequest.Email.newBuilder()
                          .setTemplateId(mailTemplateName)
                          .addAllEmailIds(Collections.singletonList(emailAdress))
                          .addAllCcEmailIds(Collections.singletonList(emailAdress))
                          .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    doReturn(new SmtpConfigResponse(SmtpConfig.builder().build(), Collections.emptyList()))
        .when(notificationSettingsService)
        .getSmtpConfigResponse(any());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("");

    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), any());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    MailTaskParams mailTaskParams = (MailTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(mailTaskParams.getEmailIds()).isNotEmpty();
    assertThat(mailTaskParams.getEmailIds()).hasSize(1);
    assertThat(mailTaskParams.getEmailIds()).contains(emailAdress);
    assertThat(mailTaskParams.getCcEmailIds()).isNotEmpty();
    assertThat(mailTaskParams.getCcEmailIds()).hasSize(1);
    assertThat(mailTaskParams.getCcEmailIds()).contains(emailAdress);
    assertEquals(emailTemplate.getSubject(), mailTaskParams.getSubject());
    assertEquals(emailTemplate.getBody(), mailTaskParams.getBody());
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_ValidCaseMailUserGroup() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setEmail(NotificationRequest.Email.newBuilder()
                          .setTemplateId(mailTemplateName)
                          .addAllEmailIds(Collections.singletonList(emailAdress))
                          .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                            .setIdentifier("identifier")
                                            .setProjectIdentifier("projectIdentifier")
                                            .setOrgIdentifier("orgIdentifier")
                                            .build())
                          .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(notificationSettingsService.getSmtpConfig(eq(accountId))).thenReturn(Optional.of(smtpConfigDefault));
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId))).thenReturn(new SmtpConfigResponse());

    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    notificationProcessingResponse = mailService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_CheckAllGaurds() {
    final NotificationSettingDTO notificationSettingDTO1 = EmailSettingDTO.builder().build();
    assertThatThrownBy(() -> mailService.sendTestNotification(notificationSettingDTO1))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO2 = EmailSettingDTO.builder().accountId(accountId).build();
    assertThatThrownBy(() -> mailService.sendTestNotification(notificationSettingDTO2))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO3 =
        EmailSettingDTO.builder().recipient("email@harness.io").build();
    assertThatThrownBy(() -> mailService.sendTestNotification(notificationSettingDTO3))
        .isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_ValidRequest() {
    final NotificationSettingDTO notificationSettingDTO4 =
        EmailSettingDTO.builder().accountId(accountId).recipient("email@harness.io").build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    boolean response = mailService.sendTestNotification(notificationSettingDTO4);
    assertTrue(response);
  }

  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_ValidRequest() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .accountId(accountId)
                                  .toRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .ccRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    NotificationTaskResponse response = mailService.sendEmail(emailDTO);
    assertTrue(response.getProcessingResponse().getResult().iterator().next());
  }

  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_no_emails() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .toRecipients(Collections.emptySet())
                                  .ccRecipients(Collections.emptySet())
                                  .accountId(accountId)
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    assertThatThrownBy(() -> mailService.sendEmail(emailDTO)).isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_invalid_emails() {
    Set<String> emails = new HashSet<>();
    emails.add("email_harness.io");
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .accountId(accountId)
                                  .toRecipients(emails)
                                  .ccRecipients(emails)
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    assertThatThrownBy(() -> mailService.sendEmail(emailDTO)).isInstanceOf(NotificationException.class);
  }
  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_no_account_id() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .toRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .ccRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    assertThatThrownBy(() -> mailService.sendEmail(emailDTO)).isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_response_wrong() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .toRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .ccRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(false)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    assertThatThrownBy(() -> mailService.sendEmail(emailDTO)).isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void sendEmail_ValidRequest_SendToNonHarnessTrue_SmtpConfigFalse() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .accountId(accountId)
                                  .toRecipients(new HashSet<>(validInvalidPair1))
                                  .ccRecipients(new HashSet<>(validInvalidPair2))
                                  .sendToNonHarnessRecipients(true)
                                  .build();
    doReturn(null).when(notificationSettingsService).getSmtpConfig(accountId);

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    ArgumentCaptor<List<String>> toCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<String>> ccCaptor = ArgumentCaptor.forClass(List.class);
    when(mailSender.send(toCaptor.capture(), ccCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of(TEST_NOTIFICATION_TEMPLATE_CONTENT));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    NotificationTaskResponse response = mailService.sendEmail(emailDTO);
    assertTrue(response.getProcessingResponse().getResult().iterator().next());

    assertThat(toCaptor.getValue().size()).isEqualTo(1);
    assertThat(ccCaptor.getValue().size()).isEqualTo(1);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void sendEmail_ValidRequest_SendToNonHarnessTrue_SmtpConfigTrue() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .accountId(accountId)
                                  .toRecipients(new HashSet<>(validInvalidPair1))
                                  .ccRecipients(new HashSet<>(validInvalidPair2))
                                  .sendToNonHarnessRecipients(true)
                                  .build();
    doReturn(new SmtpConfigResponse()).when(notificationSettingsService).getSmtpConfigResponse(accountId);
    ArgumentCaptor<DelegateTaskRequest> requestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(requestCaptor.capture()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId)))
        .thenReturn(new SmtpConfigResponse(SmtpConfig.builder().build(), Collections.emptyList()));
    mailService.sendEmail(emailDTO);
    DelegateTaskRequest request = requestCaptor.getValue();
    assertThat(((MailTaskParams) request.getTaskParameters()).getEmailIds().size()).isEqualTo(2);
    assertThat(((MailTaskParams) request.getTaskParameters()).getCcEmailIds().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();

    assertThatThrownBy(() -> mailService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid email notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();

    assertThatThrownBy(() -> mailService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Invalid email notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NoAccountIdInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setEmail(NotificationRequest.Email.newBuilder().build()).build();

    assertThatThrownBy(() -> mailService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("No account id encountered for");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setEmail(NotificationRequest.Email.newBuilder().build())
                                                  .build();

    assertThatThrownBy(() -> mailService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("No recipients found in notification request");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_NonExistingTemplateInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setEmail(NotificationRequest.Email.newBuilder()
                                                                .setTemplateId(mailTemplateName)
                                                                .addAllEmailIds(Collections.singletonList(emailAdress))
                                                                .build())
                                                  .build();
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mailService.sendSync(notificationRequest))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("Failed to send email for notification request")
        .hasMessageContaining("possibly due to no valid template with name");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithoutTemplateIdInRequest() {
    NotificationRequest.Email emailBuilder = NotificationRequest.Email.newBuilder()
                                                 .addAllEmailIds(List.of(emailAdress))
                                                 .addAllCcEmailIds(List.of(emailAdress))
                                                 .setSubject("test-subject")
                                                 .setBody("test-body")
                                                 .build();
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder().setId(id).setAccountId(accountId).setEmail(emailBuilder).build();

    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId)))
        .thenReturn(new SmtpConfigResponse(SmtpConfig.builder().build(), Collections.emptyList()));
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());

    NotificationTaskResponse notificationTaskResponse = mailService.sendSync(notificationRequest);

    verifyNoInteractions(notificationTemplateService);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    MailTaskParams mailTaskParams = (MailTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(mailTaskParams.getEmailIds()).isNotEmpty();
    assertThat(mailTaskParams.getEmailIds()).hasSize(1);
    assertThat(mailTaskParams.getEmailIds()).contains(emailAdress);
    assertThat(mailTaskParams.getCcEmailIds()).isNotEmpty();
    assertThat(mailTaskParams.getCcEmailIds()).hasSize(1);
    assertThat(mailTaskParams.getCcEmailIds()).contains(emailAdress);
    assertEquals(emailBuilder.getSubject(), mailTaskParams.getSubject());
    assertEquals(emailBuilder.getBody(), mailTaskParams.getBody());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }

  @SneakyThrows
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void sendSyncNotification_ValidCase_WithTemplateIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setEmail(NotificationRequest.Email.newBuilder()
                                                                .setTemplateId(mailTemplateName)
                                                                .addAllEmailIds(List.of(emailAdress))
                                                                .addAllCcEmailIds(List.of(emailAdress))
                                                                .setSubject("some-subject")
                                                                .setBody("some-body")
                                                                .build())
                                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(List.of(true)).shouldRetry(false).build();
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId)))
        .thenReturn(new SmtpConfigResponse(SmtpConfig.builder().build(), Collections.EMPTY_LIST));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());

    NotificationTaskResponse notificationTaskResponse = mailService.sendSync(notificationRequest);

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequestArgumentCaptor.capture());

    DelegateTaskRequest actualDelegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    MailTaskParams mailTaskParams = (MailTaskParams) actualDelegateTaskRequest.getTaskParameters();

    assertThat(mailTaskParams.getEmailIds()).isNotEmpty();
    assertThat(mailTaskParams.getEmailIds()).hasSize(1);
    assertThat(mailTaskParams.getEmailIds()).contains(emailAdress);
    assertThat(mailTaskParams.getCcEmailIds()).isNotEmpty();
    assertThat(mailTaskParams.getCcEmailIds()).hasSize(1);
    assertThat(mailTaskParams.getCcEmailIds()).contains(emailAdress);
    assertEquals(emailTemplate.getSubject(), mailTaskParams.getSubject());
    assertEquals(emailTemplate.getBody(), mailTaskParams.getBody());
    assertEquals(notificationExpectedResponse, notificationTaskResponse.getProcessingResponse());
  }
}
