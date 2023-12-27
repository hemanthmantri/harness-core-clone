/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.trafficrouting;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.task.k8s.trafficrouting.HeaderConfig;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.MatchType;
import io.harness.delegate.task.k8s.trafficrouting.RouteType;
import io.harness.delegate.task.k8s.trafficrouting.RuleType;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoute;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRouteRule;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRouteRule.TrafficRouteRuleBuilder;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoutingDestination;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_K8S})
@Singleton
@Slf4j
public class K8sTrafficRoutingHelper {
  private static final String FIELD_MUST_BE_SPECIFIED = "%s rule field or fields must be specified for %s provider";
  private static final String UNSUPPORTED_COMBINATION =
      "Unsupported combination of Provider type: %s Route type: %s and Rule type: %s";

  public Optional<K8sTrafficRoutingConfig> validateAndGetTrafficRoutingConfig(
      AbstractK8sTrafficRouting trafficRouting) {
    if (trafficRouting == null || trafficRouting.getSpec() == null) {
      return Optional.empty();
    }

    K8sTrafficRoutingConfig k8sTrafficRoutingConfig = K8sTrafficRoutingConfig.builder()
                                                          .routes(getTrafficRouts(trafficRouting))
                                                          .destinations(getTrafficRoutingDestinations(trafficRouting))
                                                          .providerConfig(trafficRouting.getSpec().toProviderConfig())
                                                          .build();

    return Optional.of(k8sTrafficRoutingConfig);
  }

  public Optional<K8sTrafficRoutingConfig> validateAndGetInheritedTrafficRoutingConfig(
      InheritK8sTrafficRouting trafficRouting) {
    if (trafficRouting == null || trafficRouting.getDestinations() == null) {
      return Optional.empty();
    }

    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder().destinations(getTrafficRoutingDestinations(trafficRouting)).build();

    return Optional.of(k8sTrafficRoutingConfig);
  }

  private List<TrafficRoute> getTrafficRouts(AbstractK8sTrafficRouting trafficRouting) {
    List<K8sTrafficRoutingRoute> routes = trafficRouting.getSpec().getRoutes();
    return routes != null
        ? routes.stream()
              .map(route
                  -> mapRoutes(trafficRouting.getProvider(), route.getRoute().getType(), route.getRoute().getRules()))
              .collect(Collectors.toList())
        : Collections.emptyList();
  }

  private TrafficRoute mapRoutes(AbstractK8sTrafficRouting.ProviderType providerType,
      K8sTrafficRoutingRoute.RouteSpec.RouteType routeType, List<K8sTrafficRoutingRule> rules) {
    return TrafficRoute.builder()
        .routeType(RouteType.HTTP)
        .rules(rules == null ? null
                             : rules.stream()
                                   .map(rule -> getTrafficRouteRule(providerType, routeType, rule.getRule()))
                                   .collect(Collectors.toList()))
        .build();
  }

  private TrafficRouteRule getTrafficRouteRule(AbstractK8sTrafficRouting.ProviderType providerType,
      K8sTrafficRoutingRoute.RouteSpec.RouteType routeType, K8sTrafficRoutingRule.Rule rule) {
    K8sTrafficRoutingRuleSpec ruleSpec = rule.getSpec();
    TrafficRouteRuleBuilder builder =
        TrafficRouteRule.builder().name(ParameterFieldHelper.getParameterFieldValue(ruleSpec.getName()));
    switch (rule.getType()) {
      case URI:
        return getUriRule(providerType, routeType, (K8sTrafficRoutingURIRuleSpec) ruleSpec, builder);
      case SCHEME:
        return getSchemaRule(providerType, routeType, (K8sTrafficRoutingSchemaRuleSpec) ruleSpec, builder);
      case METHOD:
        return getMethodRule(providerType, routeType, (K8sTrafficRoutingMethodRuleSpec) ruleSpec, builder);
      case AUTHORITY:
        return getAuthorityRule(providerType, routeType, (K8sTrafficRoutingAuthorityRuleSpec) ruleSpec, builder);
      case HEADER:
        return getHeaderRule(providerType, routeType, (K8sTrafficRoutingHeaderRuleSpec) ruleSpec, builder);
      case PORT:
        return getPortRule(providerType, routeType, (K8sTrafficRoutingPortRuleSpec) ruleSpec, builder);
      default:
        throw new InvalidArgumentsException("Unsupported Traffic route rule type");
    }
  }

  private TrafficRouteRule getPortRule(AbstractK8sTrafficRouting.ProviderType providerType,
      K8sTrafficRoutingRoute.RouteSpec.RouteType routeType, K8sTrafficRoutingPortRuleSpec ruleSpec,
      TrafficRouteRuleBuilder builder) {
    builder.ruleType(RuleType.PORT);
    if (providerType == AbstractK8sTrafficRouting.ProviderType.ISTIO) {
      if (routeType == K8sTrafficRoutingRoute.RouteSpec.RouteType.HTTP) {
        Integer value = ParameterFieldHelper.getParameterFieldValue(ruleSpec.getValue());
        if (value != null) {
          return builder.value(String.valueOf(value)).build();
        }
        throw new InvalidArgumentsException(format(FIELD_MUST_BE_SPECIFIED, "value", providerType.displayName));
      }
    }
    throw new InvalidArgumentsException(
        format(UNSUPPORTED_COMBINATION, providerType.displayName, routeType.getDisplayName(), "port"));
  }

  private TrafficRouteRule getHeaderRule(AbstractK8sTrafficRouting.ProviderType providerType,
      K8sTrafficRoutingRoute.RouteSpec.RouteType routeType, K8sTrafficRoutingHeaderRuleSpec ruleSpec,
      TrafficRouteRuleBuilder builder) {
    builder.ruleType(RuleType.HEADER);
    if (providerType == AbstractK8sTrafficRouting.ProviderType.SMI
        || providerType == AbstractK8sTrafficRouting.ProviderType.ISTIO) {
      if (routeType == K8sTrafficRoutingRoute.RouteSpec.RouteType.HTTP) {
        List<HeaderConfig> values =
            ruleSpec.getValues()
                .stream()
                .map(headerSpec
                    -> HeaderConfig.builder()
                           .key(headerSpec.getKey())
                           .value(headerSpec.getValue())
                           .matchType(providerType == AbstractK8sTrafficRouting.ProviderType.ISTIO
                                   ? mapMatchType(headerSpec.getMatchType())
                                   : null)
                           .build())
                .collect(Collectors.toList());
        if (isNotEmpty(values)) {
          return builder.headerConfigs(values).build();
        } else {
          throw new InvalidArgumentsException(format(FIELD_MUST_BE_SPECIFIED, "values", providerType.displayName));
        }
      }
    }
    throw new InvalidArgumentsException(
        format(UNSUPPORTED_COMBINATION, providerType.displayName, routeType.getDisplayName(), "header"));
  }

  private TrafficRouteRule getAuthorityRule(AbstractK8sTrafficRouting.ProviderType providerType,
      K8sTrafficRoutingRoute.RouteSpec.RouteType routeType, K8sTrafficRoutingAuthorityRuleSpec ruleSpec,
      TrafficRouteRuleBuilder builder) {
    builder.ruleType(RuleType.AUTHORITY);
    if (providerType == AbstractK8sTrafficRouting.ProviderType.ISTIO) {
      if (routeType == K8sTrafficRoutingRoute.RouteSpec.RouteType.HTTP) {
        String value = ParameterFieldHelper.getParameterFieldValue(ruleSpec.getValue());
        if (isNotEmpty(value)) {
          return builder.value(value).matchType(mapMatchType(ruleSpec.getMatchType())).build();
        } else {
          throw new InvalidArgumentsException(
              format(FIELD_MUST_BE_SPECIFIED, "value, matchType", providerType.displayName));
        }
      }
    }
    throw new InvalidArgumentsException(
        format(UNSUPPORTED_COMBINATION, providerType.displayName, routeType.getDisplayName(), "authority"));
  }
  private TrafficRouteRule getMethodRule(AbstractK8sTrafficRouting.ProviderType providerType,
      K8sTrafficRoutingRoute.RouteSpec.RouteType routeType, K8sTrafficRoutingMethodRuleSpec ruleSpec,
      TrafficRouteRuleBuilder builder) {
    builder.ruleType(RuleType.METHOD);
    String value = ruleSpec.getValue() != null ? ruleSpec.getValue().name() : null;
    if (isNotEmpty(value)) {
      return builder.value(value).matchType(mapMatchType(ruleSpec.getMatchType())).build();
    } else {
      throw new InvalidArgumentsException(format(FIELD_MUST_BE_SPECIFIED, "value", providerType.displayName));
    }
  }

  private TrafficRouteRule getSchemaRule(AbstractK8sTrafficRouting.ProviderType providerType,
      K8sTrafficRoutingRoute.RouteSpec.RouteType routeType, K8sTrafficRoutingSchemaRuleSpec schemaRuleSpec,
      TrafficRouteRuleBuilder builder) {
    builder.ruleType(RuleType.SCHEME);
    if (providerType == AbstractK8sTrafficRouting.ProviderType.ISTIO) {
      if (routeType == K8sTrafficRoutingRoute.RouteSpec.RouteType.HTTP) {
        String value = ParameterFieldHelper.getParameterFieldValue(schemaRuleSpec.getValue());
        if (isNotEmpty(value)) {
          return builder.value(value).matchType(mapMatchType(schemaRuleSpec.getMatchType())).build();
        } else {
          throw new InvalidArgumentsException(
              format(FIELD_MUST_BE_SPECIFIED, "value, matchType", providerType.displayName));
        }
      }
    }
    throw new InvalidArgumentsException(
        format(UNSUPPORTED_COMBINATION, providerType.displayName, routeType.getDisplayName(), "schema"));
  }

  private TrafficRouteRule getUriRule(AbstractK8sTrafficRouting.ProviderType providerType,
      K8sTrafficRoutingRoute.RouteSpec.RouteType routeType, K8sTrafficRoutingURIRuleSpec uriSpec,
      TrafficRouteRuleBuilder builder) {
    builder.ruleType(RuleType.URI);
    if (providerType == AbstractK8sTrafficRouting.ProviderType.SMI) {
      if (routeType == K8sTrafficRoutingRoute.RouteSpec.RouteType.HTTP) {
        String value = ParameterFieldHelper.getParameterFieldValue(uriSpec.getValue());
        if (isNotEmpty(value)) {
          return builder.value(value).build();
        } else {
          throw new InvalidArgumentsException(format(FIELD_MUST_BE_SPECIFIED, "value", providerType.displayName));
        }
      }
    } else if (providerType == AbstractK8sTrafficRouting.ProviderType.ISTIO) {
      if (routeType == K8sTrafficRoutingRoute.RouteSpec.RouteType.HTTP) {
        String value = ParameterFieldHelper.getParameterFieldValue(uriSpec.getValue());
        if (isNotEmpty(value)) {
          return builder.value(value).matchType(mapMatchType(uriSpec.getMatchType())).build();
        } else {
          throw new InvalidArgumentsException(
              format(FIELD_MUST_BE_SPECIFIED, "value, matchType", providerType.displayName));
        }
      }
    }
    throw new InvalidArgumentsException(
        format(UNSUPPORTED_COMBINATION, providerType.displayName, routeType.getDisplayName(), "uri"));
  }

  private MatchType mapMatchType(io.harness.cdng.k8s.trafficrouting.MatchType matchType) {
    if (matchType == null) {
      return MatchType.EXACT;
    }
    switch (matchType) {
      case EXACT:
        return MatchType.EXACT;
      case PREFIX:
        return MatchType.PREFIX;
      case REGEX:
        return MatchType.REGEX;
      default:
        throw new InvalidArgumentsException("Unsupported MatchType for Traffic Route Rule");
    }
  }

  private List<TrafficRoutingDestination> getTrafficRoutingDestinations(AbstractK8sTrafficRouting trafficRouting) {
    return getTrafficRoutingDestinations(trafficRouting.getSpec().getDestinations());
  }

  private List<TrafficRoutingDestination> getTrafficRoutingDestinations(InheritK8sTrafficRouting routingDestinations) {
    return getTrafficRoutingDestinations(routingDestinations.getDestinations());
  }

  private List<TrafficRoutingDestination> getTrafficRoutingDestinations(
      List<K8sTrafficRoutingDestination> k8sTrafficRoutingDestinationList) {
    return k8sTrafficRoutingDestinationList.stream()
        .map(destination
            -> TrafficRoutingDestination.builder()
                   .host(ParameterFieldHelper.getParameterFieldValue(destination.getDestination().getHost()))
                   .weight(ParameterFieldHelper.getParameterFieldValue(destination.getDestination().getWeight()))
                   .build())
        .collect(Collectors.toList());
  }
}
