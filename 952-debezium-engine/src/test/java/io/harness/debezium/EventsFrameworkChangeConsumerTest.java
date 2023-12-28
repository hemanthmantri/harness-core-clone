/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.rule.Owner;

import com.google.protobuf.InvalidProtocolBufferException;
import io.debezium.embedded.EmbeddedEngineChangeEvent;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.header.ConnectHeaders;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class EventsFrameworkChangeConsumerTest extends CategoryTest {
  private static final String DEFAULT_STRING = "default";
  @Mock Producer producer;
  private static final String collection = "coll";
  @Mock private static DebeziumProducerFactory producerFactory;
  @Mock private CfClient cfClient;
  ConsumerMode mode = ConsumerMode.SNAPSHOT;
  private static final EventsFrameworkChangeConsumerStreaming EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING =
      new EventsFrameworkChangeConsumerStreaming(ChangeConsumerConfig.builder()
                                                     .redisStreamSize(10)
                                                     .consumerType(ConsumerType.EVENTS_FRAMEWORK)
                                                     .eventsFrameworkConfiguration(null)
                                                     .build(),
          null, "coll", null);
  private static final String key_1 = "key1";
  private static final String key_2 = "key2";
  private static final String value_1 = "value1";
  private static final String value_2 = "value2";
  ChangeEvent<String, String> testRecord = new EmbeddedEngineChangeEvent<>(key_1, value_1, null, null);
  ChangeEvent<String, String> emptyRecord = new EmbeddedEngineChangeEvent<>(null, null, null, null);
  @Mock DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetValueOrDefault() {
    assertEquals(DEFAULT_STRING, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getValueOrDefault(emptyRecord));
    assertEquals(value_1, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getValueOrDefault(testRecord));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetKeyOrDefault() {
    assertEquals(DEFAULT_STRING, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getKeyOrDefault(emptyRecord));
    assertEquals(key_1, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getKeyOrDefault(testRecord));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetOperationType() {
    ConnectHeaders headers = new ConnectHeaders();
    headers.add("__op", "c", Schema.STRING_SCHEMA);
    Optional<OpType> opType = EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getOperationType(new SourceRecord(
        new HashMap<>(), new HashMap<>(), "", 0, Schema.BOOLEAN_SCHEMA, "", Schema.BOOLEAN_SCHEMA, "", 0L, headers));
    assertEquals(opType, Optional.of(OpType.CREATE));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetCollection() {
    assertEquals(collection, EVENTS_FRAMEWORK_CHANGE_CONSUMER_STREAMING.getCollection());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleBatch() throws InterruptedException, InvalidProtocolBufferException {
    EventsFrameworkChangeConsumerStreaming eventsFrameworkChangeConsumerStreaming =
        new EventsFrameworkChangeConsumerStreaming(ChangeConsumerConfig.builder()
                                                       .redisStreamSize(10)
                                                       .consumerType(ConsumerType.EVENTS_FRAMEWORK)
                                                       .eventsFrameworkConfiguration(null)
                                                       .consumerMode(mode)
                                                       .build(),
            cfClient, "coll.mode", producerFactory);
    List<ChangeEvent<String, String>> records = new ArrayList<>();

    ConnectHeaders headers_1 = new ConnectHeaders();
    headers_1.add("__op", "c", Schema.STRING_SCHEMA);

    ConnectHeaders headers_2 = new ConnectHeaders();
    headers_2.add("__op", "u", Schema.STRING_SCHEMA);

    ChangeEvent<String, String> testRecord_1 = new EmbeddedEngineChangeEvent<>(key_1, value_1, null,
        new SourceRecord(new HashMap<>(), new HashMap<>(), "topic", 0, Schema.BOOLEAN_SCHEMA, "", Schema.BOOLEAN_SCHEMA,
            "", 0L, headers_1));
    ChangeEvent<String, String> testRecord_2 = new EmbeddedEngineChangeEvent<>(key_2, value_2, null,
        new SourceRecord(new HashMap<>(), new HashMap<>(), "topic", 0, Schema.BOOLEAN_SCHEMA, "", Schema.BOOLEAN_SCHEMA,
            "", 0L, headers_2));

    records.add(testRecord_1);
    records.add(testRecord_2);
    doReturn(producer).when(producerFactory).get("topic", 10, ConsumerMode.SNAPSHOT, null);
    doNothing().when(recordCommitter).markBatchFinished();
    doNothing().when(recordCommitter).markProcessed(testRecord_1);
    doNothing().when(recordCommitter).markProcessed(testRecord_2);
    doReturn(true).when(cfClient).boolVariation(anyString(), any(), anyBoolean());
    eventsFrameworkChangeConsumerStreaming.handleBatch(records, recordCommitter);
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(producer, times(2)).send(captor.capture());

    List<DebeziumChangeEvent> events = captor.getAllValues()
                                           .stream()
                                           .map(value -> {
                                             try {
                                               return DebeziumChangeEvent.parseFrom(value.getData());
                                             } catch (InvalidProtocolBufferException e) {
                                               throw new RuntimeException(e);
                                             }
                                           })
                                           .collect(Collectors.toList());

    assertThat(events.stream().map(DebeziumChangeEvent::getKey).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(key_1, key_2);

    assertThat(events.stream().map(DebeziumChangeEvent::getValue).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(value_1, value_2);

    assertThat(events.stream().map(DebeziumChangeEvent::getOptype).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(OpType.CREATE.toString(), OpType.UPDATE.toString());

    verify(recordCommitter, times(1)).markProcessed(testRecord_1);
    verify(recordCommitter, times(1)).markProcessed(testRecord_2);
    verify(recordCommitter, times(1)).markBatchFinished();

    // verify FF service called once per batch
    verify(cfClient, times(1)).boolVariation(anyString(), any(Target.class), anyBoolean());
  }
}
