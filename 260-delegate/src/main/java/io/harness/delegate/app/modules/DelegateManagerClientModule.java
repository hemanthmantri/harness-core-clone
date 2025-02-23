/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules;

import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.managerclient.DelegateAgentManagerClientFactory;
import io.harness.security.TokenGenerator;
import io.harness.security.delegate.DelegateAuthInterceptor;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class DelegateManagerClientModule extends AbstractModule {
  @Provides
  @Singleton
  DelegateAuthInterceptor authInterceptor(final TokenGenerator tokenGenerator) {
    return new DelegateAuthInterceptor(tokenGenerator);
  }

  @Override
  protected void configure() {
    bind(DelegateAgentManagerClient.class).toProvider(DelegateAgentManagerClientFactory.class);
  }
}
