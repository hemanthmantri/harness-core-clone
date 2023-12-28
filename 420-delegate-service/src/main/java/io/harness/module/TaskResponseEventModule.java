/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.module;

import static io.harness.authorization.AuthorizationServiceHeader.DELEGATE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.TASK_RESPONSE_TOPIC;

import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.govern.ProviderModule;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.Provides;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;

@RequiredArgsConstructor
public class TaskResponseEventModule extends ProviderModule {
  private final RedisConfig config;
  @Provides
  @Named("eventsFrameworkRedissonClient")
  public RedissonClient getProducer() {
    return RedissonClientFactory.getClient(config);
  }

  @Provides
  @Named(TASK_RESPONSE_TOPIC)
  public Producer getProducer(@Named("eventsFrameworkRedissonClient") final RedissonClient client) {
    if (config.getRedisUrl().equals("dummyRedisUrl")) {
      return NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME);
    }
    return RedisProducer.of(TASK_RESPONSE_TOPIC, client, EventsFrameworkConstants.DEFAULT_MAX_TOPIC_SIZE,
        DELEGATE_SERVICE.getServiceId(), config.getEnvNamespace());
  }
}
