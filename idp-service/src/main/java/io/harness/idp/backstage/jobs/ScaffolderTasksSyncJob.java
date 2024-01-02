/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.jobs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.service.BackstageService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScaffolderTasksSyncJob implements Managed {
  private static final long DELAY_IN_MINUTES = TimeUnit.MINUTES.toMinutes(5);
  private ScheduledExecutorService executorService;
  private final BackstageService backstageService;

  @Inject
  public ScaffolderTasksSyncJob(
      @Named("scaffolderTasksSyncJob") ScheduledExecutorService executorService, BackstageService backstageService) {
    this.executorService = executorService;
    this.backstageService = backstageService;
  }

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("scaffolder-tasks-sync-job").build());
    log.info(
        "Scheduling ScaffolderTasksSyncJob with initial delay of 10 minutes from current time, will run every 5 minutes post initial run");
    executorService.scheduleWithFixedDelay(this::run, 10, DELAY_IN_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
      log.error("ScaffolderTasksSyncJob executorService terminated after the timeout of 60 seconds");
    }
  }

  public void run() {
    log.info("Scaffolder Tasks Sync Job started");
    try {
      backstageService.syncScaffolderTasks();
    } catch (Exception ex) {
      log.error("Error in Scaffolder Tasks Sync Job. Error = {}", ex.getMessage(), ex);
      throw ex;
    }
    log.info("Scaffolder Tasks Sync Job completed");
  }
}
