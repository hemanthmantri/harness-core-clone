/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.idp.provision.ProvisionConstants.PROVISION_MODULE_CONFIG;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.ScmConnectionConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.cache.CacheConfig;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.proxy.config.ProxyAllowListConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Getter
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class IdpConfiguration extends Configuration {
  @Setter @JsonProperty("mongo") private MongoConfig mongoConfig;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("logStreamingServiceConfig")
  @ConfigSecret
  private LogStreamingServiceConfiguration logStreamingServiceConfig;
  @JsonProperty("redisLockConfig") private RedisConfig redisLockConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerClientConfig;
  @JsonProperty("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("ngManagerServiceSecret") private String ngManagerServiceSecret;
  @JsonProperty("managerServiceSecret") private String managerServiceSecret;
  @JsonProperty("backstageHttpClientConfig") private ServiceHttpClientConfig backstageHttpClientConfig;
  @JsonProperty("backstageServiceSecret") private String backstageServiceSecret;
  @JsonProperty("idpServiceSecret") private String idpServiceSecret;
  @JsonProperty("jwtAuthSecret") private String jwtAuthSecret;
  @JsonProperty("jwtIdentityServiceSecret") private String jwtIdentityServiceSecret;
  @JsonProperty("onboardingModuleConfig") private OnboardingModuleConfig onboardingModuleConfig;
  @JsonProperty("gitManagerGrpcClientConfig") private GrpcClientConfig gitManagerGrpcClientConfig;
  @JsonProperty("grpcNegotiationType") NegotiationType grpcNegotiationType;
  @JsonProperty("accessControlClient") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("backstageSaToken") private String backstageSaToken;
  @JsonProperty("backstageSaCaCrt") private String backstageSaCaCrt;
  @JsonProperty("backstageMasterUrl") private String backstageMasterUrl;
  @JsonProperty("backstagePodLabel") private String backstagePodLabel;
  @JsonProperty("backstageEntitiesFetchLimit") private String backstageEntitiesFetchLimit;
  @JsonProperty("env") private String env;
  @JsonProperty("base") private String base;
  @JsonProperty("devSpaceDefaultBackstageNamespace") private String devSpaceDefaultBackstageNamespace;
  @JsonProperty("devSpaceDefaultAccountId") private String devSpaceDefaultAccountId;
  @JsonProperty(PROVISION_MODULE_CONFIG) private ProvisionModuleConfig provisionModuleConfig;
  @JsonProperty("backstageAppBaseUrl") private String backstageAppBaseUrl;
  @JsonProperty("backstagePostgresHost") private String backstagePostgresHost;
  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("shouldConfigureWithPMS") private Boolean shouldConfigureWithPMS;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("delegateSelectorsCacheMode") private String delegateSelectorsCacheMode;
  @JsonProperty("idpEncryptionSecret") private String idpEncryptionSecret;
  @JsonProperty("proxyAllowList") private ProxyAllowListConfig proxyAllowList;
  @JsonProperty("shouldConfigureWithNotification") private Boolean shouldConfigureWithNotification;
  @JsonProperty("notificationClient") private NotificationClientConfiguration notificationClientConfiguration;
  @JsonProperty("notificationConfigs") private HashMap<String, String> notificationConfigs;
  @JsonProperty("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceConfiguration;
  @JsonProperty("pipelineServiceSecret") private String pipelineServiceSecret;
  @JsonProperty("jwtExternalServiceSecret") private String jwtExternalServiceSecret;
  @JsonProperty("tiServiceConfig") private TIServiceConfig tiServiceConfig;
  @JsonProperty("scorecardScoreComputationIteratorConfig")
  private IteratorConfig scorecardScoreComputationIteratorConfig;
  @JsonProperty("cpu") private String cpu;
  @JsonProperty("scoreComputerThreadsPerCore") private String scoreComputerThreadsPerCore;
  @JsonProperty("allowedOrigins") private List<String> allowedOrigins = Lists.newArrayList();
  @JsonProperty("hostname") String hostname = "localhost";
  @JsonProperty("basePathPrefix") String basePathPrefix = "";
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty("enableAudit") private boolean enableAudit;
  private String managerTarget;
  private String managerAuthority;
  @JsonProperty("streamPerServiceConfiguration") private boolean streamPerServiceConfiguration;
  @JsonProperty("internalAccounts") private List<String> internalAccounts;
  @JsonProperty("logServiceConfig") private LogServiceConfig logServiceConfig;
  @JsonProperty("sscaServiceConfig") private SSCAServiceConfig sscaServiceConfig;
  @JsonProperty("stoServiceConfig") private STOServiceConfig stoServiceConfig;
  @JsonProperty("apiUrl") private String apiUrl;
  @JsonProperty("iacmServiceConfig") private IACMServiceConfig iacmServiceConfig;
  @JsonProperty("scmConnectionConfig") private ScmConnectionConfig scmConnectionConfig;
  @JsonProperty("pmsSdkExecutionPoolConfig") private ThreadPoolConfig pmsSdkExecutionPoolConfig;
  @JsonProperty("pmsSdkOrchestrationEventPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig;
  @JsonProperty("pmsPlanCreatorServicePoolConfig") private ThreadPoolConfig pmsPlanCreatorServicePoolConfig;
  @JsonProperty("opaClientConfig") private ServiceHttpClientConfig opaClientConfig;
  @JsonProperty("policyManagerSecret") private String policyManagerSecret;
  @JsonProperty("ciExecutionServiceConfig") private CIExecutionServiceConfig ciExecutionServiceConfig;
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("harnessCodeGitUrl") private String harnessCodeGitUrl;
  @JsonProperty("segmentConfiguration") private SegmentConfiguration segmentConfiguration;
  @JsonProperty("enableMetrics") private boolean enableMetrics;
  @JsonProperty("allowedKindsForCatalogSync") private List<String> allowedKindsForCatalogSync;
  @JsonProperty("customPlugins") private CustomPluginsConfig customPluginsConfig;

  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();
  public static final String IDP_SPEC_PACKAGE = "io.harness.spec.server.idp.v1";
  public static final String SERVICES_PROXY_PACKAGE = "io.harness.idp.proxy.services";
  public static final String DELEGATE_PROXY_PACKAGE = "io.harness.idp.proxy.delegate";
  private static final String PLUGIN_PACKAGE = "io.harness.idp.plugin.resources";
  public static final String IDP_HEALTH_PACKAGE = "io.harness.idp.health";
  public static final String LICENSING_USAGE_PACKAGE = "io.harness.licensing.usage.resources";
  public static final String IDP_LICENSE_USAGE_PACKAGE = "io.harness.idp.license.usage.resources";
  private static final String IDP_YAML_SCHEMA = "io.harness.idp.pipeline.stages.yamlschema";

  public IdpConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/");
    defaultServerFactory.setRegisterDefaultExceptionMappers(Boolean.FALSE);
    defaultServerFactory.setAdminContextPath("/admin");
    defaultServerFactory.setAdminConnectors(singletonList(getDefaultAdminConnectorFactory()));
    defaultServerFactory.setApplicationConnectors(singletonList(getDefaultApplicationConnectorFactory()));
    defaultServerFactory.setRequestLogFactory(getDefaultLogbackAccessRequestLogFactory());
    defaultServerFactory.setMaxThreads(512);
    super.setServerFactory(defaultServerFactory);
  }

  @Override
  public void setServerFactory(ServerFactory factory) {
    DefaultServerFactory defaultServerFactory = (DefaultServerFactory) factory;
    ((DefaultServerFactory) getServerFactory())
        .setApplicationConnectors(defaultServerFactory.getApplicationConnectors());
    ((DefaultServerFactory) getServerFactory()).setAdminConnectors(defaultServerFactory.getAdminConnectors());
    ((DefaultServerFactory) getServerFactory()).setRequestLogFactory(defaultServerFactory.getRequestLogFactory());
    ((DefaultServerFactory) getServerFactory()).setMaxThreads(defaultServerFactory.getMaxThreads());
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("IDP Service API Reference")
            .description(
                "This is the Open Api Spec 3 for the IDP Service. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("3.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    URL baseurl = null;
    try {
      baseurl = new URL("https", hostname, basePathPrefix);
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("The base URL of the server could not be set. {}/{}", hostname, basePathPrefix);
    }
    Collection<Class<?>> allResourceClasses = HARNESS_RESOURCE_CLASSES;
    final Set<String> resourceClasses =
        getOAS3ResourceClassesOnly(allResourceClasses).stream().map(Class::getCanonicalName).collect(toSet());
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  private ConnectorFactory getDefaultApplicationConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12003);
    return factory;
  }

  private ConnectorFactory getDefaultAdminConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12004);
    return factory;
  }

  private RequestLogFactory getDefaultLogbackAccessRequestLogFactory() {
    LogbackAccessRequestLogFactory logbackAccessRequestLogFactory = new LogbackAccessRequestLogFactory();
    FileAppenderFactory<IAccessEvent> fileAppenderFactory = new FileAppenderFactory<>();
    fileAppenderFactory.setArchive(true);
    fileAppenderFactory.setCurrentLogFilename("access.log");
    fileAppenderFactory.setThreshold(Level.ALL.toString());
    fileAppenderFactory.setArchivedLogFilenamePattern("access.%d.log.gz");
    fileAppenderFactory.setArchivedFileCount(14);
    logbackAccessRequestLogFactory.setAppenders(ImmutableList.of(fileAppenderFactory));
    return logbackAccessRequestLogFactory;
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }

  public static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream()
        .filter(x -> x.isAnnotationPresent(Tag.class))
        .map(aClass -> aClass.getPackage().getName())
        .collect(toSet());
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly(Collection<Class<?>> allResourceClasses) {
    return allResourceClasses.stream().collect(Collectors.toList());
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), IDP_SPEC_PACKAGE, SERVICES_PROXY_PACKAGE,
                DELEGATE_PROXY_PACKAGE, PLUGIN_PACKAGE, IDP_HEALTH_PACKAGE, IDP_YAML_SCHEMA, LICENSING_USAGE_PACKAGE,
                IDP_LICENSE_USAGE_PACKAGE))
        .collect(Collectors.toSet());
  }
}
