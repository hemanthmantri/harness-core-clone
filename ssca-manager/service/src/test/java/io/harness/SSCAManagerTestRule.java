/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static org.mockito.Mockito.mock;

import io.harness.account.AccountClient;
import io.harness.factory.ClosingFactory;
import io.harness.govern.ProviderModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.opaclient.OpaServiceClient;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.persistence.HPersistence;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.SSCAManagerModuleRegistrars;
import io.harness.spec.server.ssca.v1.EnforcementApi;
import io.harness.spec.server.ssca.v1.OrchestrationApi;
import io.harness.spec.server.ssca.v1.SbomProcessorApi;
import io.harness.spec.server.ssca.v1.TokenApi;
import io.harness.springdata.HTransactionTemplate;
import io.harness.ssca.S3Config;
import io.harness.ssca.api.EnforcementApiImpl;
import io.harness.ssca.api.OrchestrationApiImpl;
import io.harness.ssca.api.SbomProcessorApiImpl;
import io.harness.ssca.api.TokenApiImpl;
import io.harness.ssca.beans.ElasticSearchConfig;
import io.harness.ssca.beans.PolicyType;
import io.harness.ssca.search.ElasticSearchIndexManager;
import io.harness.ssca.search.SSCAIndexManager;
import io.harness.ssca.search.SearchService;
import io.harness.ssca.search.SearchServiceImpl;
import io.harness.ssca.services.ArtifactService;
import io.harness.ssca.services.ArtifactServiceImpl;
import io.harness.ssca.services.BaselineService;
import io.harness.ssca.services.BaselineServiceImpl;
import io.harness.ssca.services.CdInstanceSummaryService;
import io.harness.ssca.services.CdInstanceSummaryServiceImpl;
import io.harness.ssca.services.ConfigService;
import io.harness.ssca.services.ConfigServiceImpl;
import io.harness.ssca.services.EnforcementResultService;
import io.harness.ssca.services.EnforcementResultServiceImpl;
import io.harness.ssca.services.EnforcementStepService;
import io.harness.ssca.services.EnforcementStepServiceImpl;
import io.harness.ssca.services.EnforcementSummaryService;
import io.harness.ssca.services.EnforcementSummaryServiceImpl;
import io.harness.ssca.services.FeatureFlagService;
import io.harness.ssca.services.FeatureFlagServiceImpl;
import io.harness.ssca.services.NextGenService;
import io.harness.ssca.services.NextGenServiceImpl;
import io.harness.ssca.services.NormalisedSbomComponentService;
import io.harness.ssca.services.NormalisedSbomComponentServiceImpl;
import io.harness.ssca.services.OpaPolicyEvaluationService;
import io.harness.ssca.services.OrchestrationStepService;
import io.harness.ssca.services.OrchestrationStepServiceImpl;
import io.harness.ssca.services.PolicyEvaluationService;
import io.harness.ssca.services.PolicyMgmtService;
import io.harness.ssca.services.PolicyMgmtServiceImpl;
import io.harness.ssca.services.RuleEngineService;
import io.harness.ssca.services.RuleEngineServiceImpl;
import io.harness.ssca.services.S3StoreService;
import io.harness.ssca.services.S3StoreServiceImpl;
import io.harness.ssca.services.ScorecardService;
import io.harness.ssca.services.ScorecardServiceImpl;
import io.harness.ssca.services.SscaPolicyEvaluationService;
import io.harness.ssca.services.drift.SbomDriftService;
import io.harness.ssca.services.drift.SbomDriftServiceImpl;
import io.harness.ssca.services.exemption.ExemptionService;
import io.harness.ssca.services.exemption.ExemptionServiceImpl;
import io.harness.ssca.services.remediation_tracker.RemediationTrackerService;
import io.harness.ssca.services.remediation_tracker.RemediationTrackerServiceImpl;
import io.harness.ssca.services.user.UserService;
import io.harness.ssca.services.user.UserServiceImpl;
import io.harness.ssca.ticket.TicketServiceRestClientService;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.user.remote.UserClient;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class SSCAManagerTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  ClosingFactory closingFactory;

  public SSCAManagerTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      public S3Config s3Config() {
        return S3Config.builder().build();
      }

      @Provides
      @Singleton
      public AmazonS3 s3Client() {
        return AmazonS3ClientBuilder.standard().build();
      }

      @Provides
      @Singleton
      @Named("jwtAuthSecret")
      public String jwtAuthSecret() {
        return "jstAuthSecret";
      }

      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(SSCAManagerModuleRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(SSCAManagerModuleRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(SSCAManagerModuleRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(SSCAManagerModuleRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
      }

      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }

      @Provides
      @Singleton
      @Named("isElasticSearchEnabled")
      public boolean isElasticSearchEnabled() {
        return false;
      }

      @Provides
      @Singleton
      @Named("elasticsearch")
      public ElasticSearchConfig elasticSearchConfig() {
        return ElasticSearchConfig.builder().url("url").apiKey("apiKey").indexName("harness-ssca").build();
      }

      @Provides
      @Singleton
      @Named("sscaManagerServiceSecret")
      public String sscaManagerServiceSecret() {
        return "sscaManagerServiceSecret";
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(SbomProcessorApi.class).to(SbomProcessorApiImpl.class);
        bind(EnforcementApi.class).to(EnforcementApiImpl.class);
        bind(OrchestrationApi.class).to(OrchestrationApiImpl.class);
        bind(ArtifactService.class).to(ArtifactServiceImpl.class);
        bind(BaselineService.class).to(BaselineServiceImpl.class);
        bind(OrchestrationStepService.class).to(OrchestrationStepServiceImpl.class);
        bind(SbomDriftService.class).to(SbomDriftServiceImpl.class);
        bind(EnforcementStepService.class).to(EnforcementStepServiceImpl.class);
        bind(RuleEngineService.class).to(RuleEngineServiceImpl.class);
        bind(NormalisedSbomComponentService.class).to(NormalisedSbomComponentServiceImpl.class);
        bind(EnforcementResultService.class).to(EnforcementResultServiceImpl.class);
        bind(EnforcementSummaryService.class).to(EnforcementSummaryServiceImpl.class);
        bind(ConfigService.class).to(ConfigServiceImpl.class);
        bind(NextGenService.class).toInstance(mock(NextGenServiceImpl.class));
        bind(CdInstanceSummaryService.class).to(CdInstanceSummaryServiceImpl.class);
        bind(ScorecardService.class).to(ScorecardServiceImpl.class);
        bind(S3StoreService.class).to(S3StoreServiceImpl.class);
        bind(TokenApi.class).to(TokenApiImpl.class);
        bind(PipelineServiceClient.class).toInstance(mock(PipelineServiceClient.class));
        bind(OutboxService.class).toInstance(mock(OutboxServiceImpl.class));
        bind(OpaServiceClient.class).toInstance(mock(OpaServiceClient.class));
        bind(PolicyMgmtService.class).toInstance(mock(PolicyMgmtServiceImpl.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagServiceImpl.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(UserClient.class).toInstance(mock(UserClient.class));
        bind(SearchService.class).to(SearchServiceImpl.class);
        bind(ElasticsearchClient.class).toInstance(mock(ElasticsearchClient.class));
        bind(ElasticSearchIndexManager.class).annotatedWith(Names.named("SSCA")).to(SSCAIndexManager.class);
        bind(RemediationTrackerService.class).to(RemediationTrackerServiceImpl.class);
        bind(TicketServiceRestClientService.class).toInstance(mock(TicketServiceRestClientService.class));
        bind(ExemptionService.class).toInstance(mock(ExemptionServiceImpl.class));
        bind(UserService.class).toInstance(mock(UserServiceImpl.class));
        MapBinder<PolicyType, PolicyEvaluationService> policyEvaluationServiceMapBinder =
            MapBinder.newMapBinder(binder(), PolicyType.class, PolicyEvaluationService.class);
        policyEvaluationServiceMapBinder.addBinding(PolicyType.OPA)
            .to(OpaPolicyEvaluationService.class)
            .in(Scopes.SINGLETON);
        policyEvaluationServiceMapBinder.addBinding(PolicyType.SSCA)
            .to(SscaPolicyEvaluationService.class)
            .in(Scopes.SINGLETON);
      }
    });
    modules.add(TimeModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(mongoTypeModule(annotations));
    modules.add(new SSCAPersistenceTestModule());
    return modules;
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object o) {
    return applyInjector(log, statement, frameworkMethod, o);
  }
}
