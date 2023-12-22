/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.googlefunctions.GoogleCloudFunctionGenOneClient;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.api.core.ApiFuture;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.retrying.RetryingFuture;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CloudFunctionStatus;
import com.google.cloud.functions.v1.CreateFunctionRequest;
import com.google.cloud.functions.v1.UpdateFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.common.util.concurrent.TimeLimiter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GoogleFunctionGenOneCommandTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final String PROJECT = "cd-play";
  private final String REGION = "us-east1";
  private final String BUCKET = "bucket";
  private final String FUNCTION = "function";
  private final Long TIMEOUT = 10L;

  @InjectMocks private GoogleFunctionGenOneCommandTaskHelper googleFunctionGenOneCommandTaskHelper;

  private GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig;
  @Mock private LogCallback logCallback;
  @Mock private GoogleCloudFunctionGenOneClient googleCloudFunctionGenOneClient;
  @Mock private TimeLimiter timeLimiter;
  private CloudFunction function;
  @Mock private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Before
  public void setUp() throws Exception {
    gcpGoogleFunctionInfraConfig =
        GcpGoogleFunctionInfraConfig.builder()
            .region(REGION)
            .project(PROJECT)
            .gcpConnectorDTO(GcpConnectorDTO.builder()
                                 .credential(GcpConnectorCredentialDTO.builder()
                                                 .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                                                 .build())
                                 .build())
            .build();
    function = CloudFunction.newBuilder().setName("FUNCTION").setStatus(CloudFunctionStatus.OFFLINE).build();

    OperationFuture<Object, Object> operationFuture = getOperationFunctionFuture();

    doReturn(operationFuture).when(googleCloudFunctionGenOneClient).createFunction(any(), any());

    doReturn(operationFuture).when(googleCloudFunctionGenOneClient).updateFunction(any(), any());

    doReturn(operationFuture).when(googleCloudFunctionGenOneClient).deleteFunction(any(), any());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void createFunctionTest() throws ExecutionException, InterruptedException {
    when(googleCloudFunctionGenOneClient.getFunction(any(), any())).thenReturn(function);
    CloudFunction function = googleFunctionGenOneCommandTaskHelper.createFunction(
        CreateFunctionRequest.newBuilder().build(), gcpGoogleFunctionInfraConfig, logCallback, TIMEOUT);
    verify(googleCloudFunctionGenOneClient).createFunction(any(), any());
    verify(googleCloudFunctionGenOneClient).getFunction(any(), any());
    assertThat(function.getName()).isEqualTo(function.getName());
    assertThat(function.getStatus()).isEqualTo(function.getStatus());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void updateFunctionTest() throws ExecutionException, InterruptedException {
    when(googleCloudFunctionGenOneClient.getFunction(any(), any())).thenReturn(function);
    CloudFunction function = googleFunctionGenOneCommandTaskHelper.updateFunction(
        UpdateFunctionRequest.newBuilder().build(), gcpGoogleFunctionInfraConfig, logCallback, TIMEOUT);
    verify(googleCloudFunctionGenOneClient).updateFunction(any(), any());
    verify(googleCloudFunctionGenOneClient).getFunction(any(), any());
    assertThat(function.getName()).isEqualTo(function.getName());
    assertThat(function.getStatus()).isEqualTo(function.getStatus());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void deleteFunctionTest() throws ExecutionException, InterruptedException {
    when(googleCloudFunctionGenOneClient.getFunction(any(), any())).thenReturn(function);
    googleFunctionGenOneCommandTaskHelper.deleteFunction(FUNCTION, gcpGoogleFunctionInfraConfig, logCallback, TIMEOUT);
    verify(googleCloudFunctionGenOneClient).deleteFunction(any(), any());
    verify(googleCloudFunctionGenOneClient).getFunction(any(), any());
    assertThat(function.getName()).isEqualTo(function.getName());
    assertThat(function.getStatus()).isEqualTo(function.getStatus());
  }

  private OperationFuture<Object, Object> getOperationFunctionFuture() {
    return new OperationFuture<>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return false;
      }

      @Override
      public Function get() throws InterruptedException, ExecutionException {
        return null;
      }

      @Override
      public Function get(long timeout, @NotNull TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return null;
      }

      @Override
      public void addListener(Runnable runnable, Executor executor) {}

      @Override
      public String getName() throws InterruptedException, ExecutionException {
        return null;
      }

      @Override
      public ApiFuture<OperationSnapshot> getInitialFuture() {
        return new ApiFuture<OperationSnapshot>() {
          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
          }

          @Override
          public boolean isCancelled() {
            return false;
          }

          @Override
          public boolean isDone() {
            return false;
          }

          @Override
          public OperationSnapshot get() throws InterruptedException, ExecutionException {
            return null;
          }

          @Override
          public OperationSnapshot get(long timeout, @NotNull TimeUnit unit)
              throws InterruptedException, ExecutionException, TimeoutException {
            return null;
          }

          @Override
          public void addListener(Runnable runnable, Executor executor) {}
        };
      }

      @Override
      public RetryingFuture<OperationSnapshot> getPollingFuture() {
        return null;
      }

      @Override
      public ApiFuture<Object> peekMetadata() {
        return null;
      }

      @Override
      public ApiFuture<Object> getMetadata() {
        return null;
      }
    };
  }
}
