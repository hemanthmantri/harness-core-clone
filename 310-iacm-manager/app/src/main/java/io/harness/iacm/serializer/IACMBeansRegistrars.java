/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.serializer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.serializer.morphia.CIExecutionMorphiaRegistrar;
import io.harness.cimanager.serializer.CIContractsKryoRegistrar;
import io.harness.cimanager.serializer.CIContractsMorphiaRegistrar;
import io.harness.iacm.serializer.kryo.IACMBeansKryoRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.AccessControlClientRegistrars;
import io.harness.serializer.ConnectorBeansRegistrars;
import io.harness.serializer.ConnectorNextGenRegistrars;
import io.harness.serializer.ContainerRegistrars;
import io.harness.serializer.DelegateServiceBeansRegistrars;
import io.harness.serializer.DelegateTaskRegistrars;
import io.harness.serializer.FeatureFlagBeansRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.LicenseManagerRegistrars;
import io.harness.serializer.NGCommonModuleRegistrars;
import io.harness.serializer.NGCoreBeansRegistrars;
import io.harness.serializer.PrimaryVersionManagerRegistrars;
import io.harness.serializer.ProjectAndOrgRegistrars;
import io.harness.serializer.SMCoreRegistrars;
import io.harness.serializer.SecretManagerClientRegistrars;
import io.harness.serializer.WaitEngineRegistrars;
import io.harness.serializer.YamlBeansModuleRegistrars;
import io.harness.serializer.common.CommonsRegistrars;
import io.harness.serializer.kryo.CIBeansKryoRegistrar;
import io.harness.serializer.kryo.NgPersistenceKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.morphia.CIBeansMorphiaRegistrar;
import io.harness.serializer.morphia.NgPersistenceMorphiaRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.YamlMorphiaRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IACM)
@UtilityClass
public class IACMBeansRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(ProjectAndOrgRegistrars.kryoRegistrars)
          .addAll(NGCoreBeansRegistrars.kryoRegistrars)
          .addAll(SecretManagerClientRegistrars.kryoRegistrars)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .addAll(NGCommonModuleRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(WaitEngineRegistrars.kryoRegistrars)
          .addAll(DelegateServiceBeansRegistrars.kryoRegistrars)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .addAll(ConnectorNextGenRegistrars.kryoRegistrars)
          .addAll(LicenseManagerRegistrars.kryoRegistrars)
          .addAll(ContainerRegistrars.kryoRegistrars)
          .add(NgPersistenceKryoRegistrar.class)
          .add(IACMBeansKryoRegistrar.class)
          .add(CIContractsKryoRegistrar.class)
          .add(NotificationBeansKryoRegistrar.class)
          .add(CIBeansKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(ProjectAndOrgRegistrars.morphiaRegistrars)
          .addAll(NGCoreBeansRegistrars.morphiaRegistrars)
          .addAll(SecretManagerClientRegistrars.morphiaRegistrars)
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .addAll(NGCommonModuleRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(WaitEngineRegistrars.morphiaRegistrars)
          .addAll(DelegateServiceBeansRegistrars.morphiaRegistrars)
          .addAll(AccessControlClientRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(CommonsRegistrars.morphiaRegistrars)
          .addAll(ConnectorNextGenRegistrars.morphiaRegistrars)
          .addAll(LicenseManagerRegistrars.morphiaRegistrars)
          .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
          .addAll(ContainerRegistrars.morphiaRegistrars)
          .add(NotificationBeansMorphiaRegistrar.class)
          .add(CIBeansMorphiaRegistrar.class)
          .add(CIContractsMorphiaRegistrar.class)
          .add(CIExecutionMorphiaRegistrar.class)
          .add(YamlMorphiaRegistrar.class)
          .add(NgPersistenceMorphiaRegistrar.class)
          .build();
}
