/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdstage;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdstage.remote.CDStageConfigClient;
import io.harness.cdstage.remote.CDStageConfigClientHttpFactory;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(IDP)
public class CDStageConfigResourceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig ngManagerClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public CDStageConfigResourceClientModule(
      ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret, String clientId) {
    this.ngManagerClientConfig = ngManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private CDStageConfigClientHttpFactory cdStageConfigClientHttpFactory(KryoConverterFactory kryoConverterFactory) {
    return new CDStageConfigClientHttpFactory(
        ngManagerClientConfig, serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    bind(CDStageConfigClient.class).toProvider(CDStageConfigClientHttpFactory.class).in(Scopes.SINGLETON);
  }
}
