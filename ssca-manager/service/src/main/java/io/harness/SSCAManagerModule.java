/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.SSCA;
import static io.harness.audit.ResourceTypeConstants.SSCA_ARTIFACT;
import static io.harness.authorization.AuthorizationServiceHeader.SSCA_SERVICE;
import static io.harness.lock.DistributedLockImplementation.REDIS;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.exception.GeneralException;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.opaclient.OpaClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UserProvider;
import io.harness.pipeline.remote.PipelineRemoteClientModule;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.SSCAManagerModuleRegistrars;
import io.harness.spec.server.ssca.v1.ArtifactApi;
import io.harness.spec.server.ssca.v1.BaselineApi;
import io.harness.spec.server.ssca.v1.ConfigApi;
import io.harness.spec.server.ssca.v1.EnforcementApi;
import io.harness.spec.server.ssca.v1.ExemptionsApi;
import io.harness.spec.server.ssca.v1.OrchestrationApi;
import io.harness.spec.server.ssca.v1.RemediationApi;
import io.harness.spec.server.ssca.v1.SbomProcessorApi;
import io.harness.spec.server.ssca.v1.ScorecardApi;
import io.harness.spec.server.ssca.v1.TokenApi;
import io.harness.ssca.S3Config;
import io.harness.ssca.api.ArtifactApiImpl;
import io.harness.ssca.api.BaselineApiImpl;
import io.harness.ssca.api.ConfigApiImpl;
import io.harness.ssca.api.EnforcementApiImpl;
import io.harness.ssca.api.ExemptionsApiImpl;
import io.harness.ssca.api.OrchestrationApiImpl;
import io.harness.ssca.api.RemediationTrackerApiImpl;
import io.harness.ssca.api.SbomProcessorApiImpl;
import io.harness.ssca.api.ScorecardApiImpl;
import io.harness.ssca.api.TokenApiImpl;
import io.harness.ssca.beans.ElasticSearchConfig;
import io.harness.ssca.beans.PolicyType;
import io.harness.ssca.events.handler.SSCAArtifactEventHandler;
import io.harness.ssca.events.handler.SSCAOutboxEventHandler;
import io.harness.ssca.eventsframework.SSCAEventsFrameworkModule;
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
import io.harness.ssca.ticket.TicketServiceRestClientService;
import io.harness.ssca.ticket.TicketServiceRestClientServiceImpl;
import io.harness.time.TimeModule;
import io.harness.token.TokenClientModule;
import io.harness.user.UserClientModule;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.core.convert.converter.Converter;

@Slf4j
@OwnedBy(SSCA)
public class SSCAManagerModule extends AbstractModule {
  private final io.harness.SSCAManagerConfiguration configuration;

  private static SSCAManagerModule sscaManagerModule;
  private SSCAManagerModule(io.harness.SSCAManagerConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  protected void configure() {
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new UserPrincipalUserProvider();
      }
    });
    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(SSCAOutboxEventHandler.class);
    install(new io.harness.SSCAManagerModulePersistence());
    bind(ScorecardApi.class).to(ScorecardApiImpl.class);
    bind(ConfigApi.class).to(ConfigApiImpl.class);
    bind(ConfigService.class).to(ConfigServiceImpl.class);
    bind(ScorecardService.class).to(ScorecardServiceImpl.class);
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(SbomProcessorApi.class).to(SbomProcessorApiImpl.class);
    bind(TokenApi.class).to(TokenApiImpl.class);
    bind(EnforcementApi.class).to(EnforcementApiImpl.class);
    bind(BaselineApi.class).to(BaselineApiImpl.class);
    bind(OrchestrationApi.class).to(OrchestrationApiImpl.class);
    bind(ArtifactService.class).to(ArtifactServiceImpl.class);
    bind(OrchestrationStepService.class).to(OrchestrationStepServiceImpl.class);
    bind(EnforcementStepService.class).to(EnforcementStepServiceImpl.class);
    bind(RuleEngineService.class).to(RuleEngineServiceImpl.class);
    bind(EnforcementResultService.class).to(EnforcementResultServiceImpl.class);
    bind(BaselineService.class).to(BaselineServiceImpl.class);
    bind(EnforcementSummaryService.class).to(EnforcementSummaryServiceImpl.class);
    bind(NextGenService.class).to(NextGenServiceImpl.class);
    bind(S3StoreService.class).to(S3StoreServiceImpl.class);
    bind(NormalisedSbomComponentService.class).to(NormalisedSbomComponentServiceImpl.class);
    bind(ArtifactApi.class).to(ArtifactApiImpl.class);
    bind(CdInstanceSummaryService.class).to(CdInstanceSummaryServiceImpl.class);
    bind(PolicyMgmtService.class).to(PolicyMgmtServiceImpl.class);
    bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
    bind(SbomDriftService.class).to(SbomDriftServiceImpl.class);
    bind(SearchService.class).to(SearchServiceImpl.class);
    bind(RemediationTrackerService.class).to(RemediationTrackerServiceImpl.class);
    bind(RemediationApi.class).to(RemediationTrackerApiImpl.class);
    bind(ElasticSearchIndexManager.class).annotatedWith(Names.named("SSCA")).to(SSCAIndexManager.class);
    bind(TicketServiceRestClientService.class).to(TicketServiceRestClientServiceImpl.class);
    bind(ExemptionService.class).to(ExemptionServiceImpl.class);
    bind(ExemptionsApi.class).to(ExemptionsApiImpl.class);
    MapBinder<PolicyType, PolicyEvaluationService> policyEvaluationServiceMapBinder =
        MapBinder.newMapBinder(binder(), PolicyType.class, PolicyEvaluationService.class);
    policyEvaluationServiceMapBinder.addBinding(PolicyType.OPA)
        .to(OpaPolicyEvaluationService.class)
        .in(Scopes.SINGLETON);
    policyEvaluationServiceMapBinder.addBinding(PolicyType.SSCA)
        .to(SscaPolicyEvaluationService.class)
        .in(Scopes.SINGLETON);
    install(new TokenClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getNgManagerServiceSecret(), SSCA_SERVICE.getServiceId()));
    install(new SSCAEventsFrameworkModule(
        this.configuration.getEventsFrameworkConfiguration(), this.configuration.getDebeziumConsumerConfigs()));
    install(PrimaryVersionManagerModule.getInstance());
    install(PersistentLockModule.getInstance());
    install(TimeModule.getInstance());
    install(new PipelineRemoteClientModule(configuration.getPipelineServiceConfiguration(),
        configuration.getPipelineServiceSecret(), SSCA_SERVICE.getServiceId()));
    install(new OpaClientModule(configuration.getPolicyMgmtServiceConfiguration(),
        configuration.getPolicyMgmtServiceSecret(), SSCA_SERVICE.getServiceId()));
    install(new TransactionOutboxModule(
        DEFAULT_OUTBOX_POLL_CONFIGURATION, SSCA_SERVICE.getServiceId(), configuration.isExportMetricsToStackDriver()));
    install(new AccountClientModule(configuration.getManagerClientConfig(), configuration.getSscaManagerServiceSecret(),
        SSCA_SERVICE.getServiceId()));
    install(AccessControlClientModule.getInstance(
        this.configuration.getAccessControlClientConfiguration(), SSCA_SERVICE.getServiceId()));
    install(UserClientModule.getInstance(this.configuration.getManagerClientConfig(),
        this.configuration.getNgManagerServiceSecret(), SSCA_SERVICE.getServiceId()));
  }

  @Provides
  @Singleton
  @Named("policyMgmtServiceClientConfig")
  public ServiceHttpClientConfig policyMgmtServiceClientConfig() {
    return this.configuration.getPolicyMgmtServiceConfiguration();
  }

  @Provides
  @Singleton
  @Named("policyMgmtServiceSecret")
  public String policyMgmtServiceSecret() {
    return this.configuration.getPolicyMgmtServiceSecret();
  }

  @Provides
  @Singleton
  @Named("ngManagerServiceHttpClientConfig")
  public ServiceHttpClientConfig ngManagerServiceHttpClientConfig() {
    return this.configuration.getNgManagerServiceHttpClientConfig();
  }
  @Provides
  @Singleton
  @Named("ngManagerServiceSecret")
  public String ngManagerServiceSecret() {
    return this.configuration.getNgManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("sscaManagerServiceSecret")
  public String sscaManagerServiceSecret() {
    return this.configuration.getSscaManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("isElasticSearchEnabled")
  public boolean isElasticSearchEnabled() {
    return this.configuration.isEnableElasticsearch();
  }

  @Provides
  @Singleton
  @Named("elasticsearch")
  public ElasticSearchConfig elasticSearchConfig() {
    return this.configuration.getElasticSearchConfig();
  }

  @Provides
  @Singleton
  @Named("jwtAuthSecret")
  public String jwtAuthSecret() {
    return this.configuration.getJwtAuthSecret();
  }

  @Provides
  @Singleton
  public S3Config s3Config() {
    return this.configuration.getS3Config();
  }

  @Provides
  @Singleton
  public AmazonS3 s3Client() {
    BasicAWSCredentials googleCreds = new BasicAWSCredentials(
        configuration.getS3Config().getAccessKeyId(), configuration.getS3Config().getAccessSecretKey());

    return AmazonS3ClientBuilder.standard()
        .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(configuration.getS3Config().getEndpoint(), "auto"))
        .withCredentials(new AWSStaticCredentialsProvider(googleCreds))
        /*Added this as suggested here: https://github.com/aws/aws-sdk-java-v2/issues/3524#issue-1426861417 to fix the
         * DNS issue
         */
        .withPathStyleAccessEnabled(Boolean.TRUE)
        .build();
  }

  @Provides
  @Singleton
  public ElasticsearchClient elasticsearchClient() {
    try {
      RestClient restClient = RestClient.builder(HttpHost.create(configuration.getElasticSearchConfig().getUrl()))
                                  .setDefaultHeaders(new Header[] {new BasicHeader(
                                      "Authorization", "ApiKey " + configuration.getElasticSearchConfig().getApiKey())})
                                  .build();

      ElasticsearchTransport transport =
          new RestClientTransport(restClient, new JacksonJsonpMapper(NG_DEFAULT_OBJECT_MAPPER));
      return new ElasticsearchClient(transport);
    } catch (Exception e) {
      throw new GeneralException("Failed to create Elasticsearch client", e);
    }
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return configuration.getRedisLockConfig();
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return configuration.getDistributedLockImplementation() == null ? REDIS
                                                                    : configuration.getDistributedLockImplementation();
  }

  @Provides
  @Singleton
  @Named("pipelineServiceClientConfigs")
  public ServiceHttpClientConfig pipelineServiceConfiguration() {
    return this.configuration.getPipelineServiceConfiguration();
  }

  @Provides
  @Singleton
  public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(SSCAManagerModuleRegistrars.kryoRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(SSCAManagerModuleRegistrars.morphiaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends TypeConverter>> morphiaConverters() {
    return ImmutableSet.<Class<? extends TypeConverter>>builder()
        .addAll(SSCAManagerModuleRegistrars.morphiaConverters)
        .build();
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
  public MongoConfig mongoConfig() {
    return configuration.getMongoConfig();
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return Collections.emptyMap();
  }

  public static SSCAManagerModule getInstance(io.harness.SSCAManagerConfiguration sscaManagerConfiguration) {
    if (sscaManagerModule == null) {
      return new SSCAManagerModule(sscaManagerConfiguration);
    }
    return sscaManagerModule;
  }

  private void registerOutboxEventHandlers() {
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(SSCA_ARTIFACT).to(SSCAArtifactEventHandler.class);
  }

  @Provides
  @Singleton
  public ObjectMapper getObjectMapper() {
    return NG_DEFAULT_OBJECT_MAPPER;
  }
}
