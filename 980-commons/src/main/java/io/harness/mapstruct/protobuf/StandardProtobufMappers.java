/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mapstruct.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import java.time.Duration;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Standard Protobuf Mappers for common types that are not mapped by default. Like Duration, Timestamp, byte[] etc.
 * Some standard implementations can be found in <a
 * href="https://github.com/entur/mapstruct-spi-protobuf/blob/develop/support-core/src/main/java/no/entur/abt/mapstruct/common/ProtobufStandardMappings.java">protobuf-spi</a>,
 * but we don't want to depend on this library because it was only vetted as compile time dependency and not runtime
 * dependency.
 */
@Mapper
public interface StandardProtobufMappers {
  StandardProtobufMappers INSTANCE = Mappers.getMapper(StandardProtobufMappers.class);
  default Duration mapDuration(final com.google.protobuf.Duration t) {
    if (t != null) {
      return Duration.ofSeconds(t.getSeconds(), t.getNanos());
    } else {
      return null;
    }
  }

  default com.google.protobuf.Duration mapDuration(final Duration t) {
    if (t != null) {
      return Durations.fromNanos(t.toNanos());
    } else {
      return null;
    }
  }

  default Instant mapToInstant(final Timestamp timestamp) {
    if (timestamp == null) {
      return null;
    }
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  default Timestamp mapToTimestamp(final Instant instant) {
    if (instant == null) {
      return null;
    }

    return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
  }

  default ByteString mapByteString(final byte[] array) {
    if (array == null) {
      return ByteString.EMPTY;
    }
    return ByteString.copyFrom(array);
  }

  default byte[] mapByteString(final ByteString in) {
    if (in != null && !in.isEmpty()) {
      return in.toByteArray();
    }

    return null;
  }
}
