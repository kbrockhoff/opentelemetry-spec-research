/*
 * Copyright 2019, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.futureapi.events;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.padStart;
import static io.opentelemetry.futureapi.events.AttributeUtils.convertStackTraceElement2StackFrame;
import static io.opentelemetry.futureapi.events.EventConstants.ATTR_ERROR_MESSAGE;
import static io.opentelemetry.futureapi.events.EventConstants.ATTR_ERROR_OBJECT;
import static io.opentelemetry.futureapi.events.EventConstants.EVENT_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.protobuf.Any;
import com.google.protobuf.GeneratedMessageV3;
import io.opentelemetry.proto.common.v1.AttributeKeyValue;
import io.opentelemetry.proto.common.v1.AttributeKeyValue.ValueType;
import io.opentelemetry.proto.events.v1.ErrorData;
import io.opentelemetry.proto.events.v1.ExceptionData;
import io.opentelemetry.proto.events.v1.StackTrace;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import io.opentelemetry.proto.events.v1.Event;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converts a {@link Throwable} with potentially nested throwables into an OpenTelementry {@code
 * error} event.
 */
public class ThrowableTranslator {

  private static final int DEFAULT_MAX_STACK_TRACE_LENGTH = 50;
  private static final Logger LOGGER = Logger.getLogger(ThrowableTranslator.class.getName());

  private int maxStackTraceLength;

  public ThrowableTranslator() {
    this(DEFAULT_MAX_STACK_TRACE_LENGTH);
  }

  public ThrowableTranslator(int maxStackTraceLength) {
    this.maxStackTraceLength = maxStackTraceLength;
  }

  public Event translateThrowable(Throwable source, Map<String, Object> parameters) {
    checkNotNull(source, "source is required");
    Event.Builder builder = Event.newBuilder();
    Instant ts = Instant.now();
    builder
        .setTimeUnixnano(ts.getEpochSecond() * 1000000000L + ts.getNano())
        .setDescription(EVENT_ERROR)
        .addAttributes(buildStringAttribute(
            ATTR_ERROR_MESSAGE, constructMessage(source)))
        .addAttributes(buildAnyAttribute(
            ATTR_ERROR_OBJECT, constructErrorData(source, parameters)));
    return builder.build();
  }

  private String constructMessage(Throwable throwable) {
    String message = throwable.getLocalizedMessage();
    if (isNullOrEmpty(message)) {
      message = throwable.getClass().getName();
    }
    return message;
  }

  private ErrorData constructErrorData(Throwable throwable, Map<String, Object> arguments) {
    ErrorData.Builder builder = ErrorData.newBuilder();
    MessageDigest hash = newMessageDigest();
    MessageDigest issue = newMessageDigest();
    String id = generateId();
    ExceptionData.Builder exception = constructException(throwable, id, hash, issue);
    Throwable nextNode = extractCause(throwable);
    while (null != nextNode) {
      final Throwable currentNode = nextNode;
      id = generateId();
      exception.setCause(id);
      builder.addExceptions(exception.build());
      exception = constructException(currentNode, id, hash, issue);
      nextNode = extractCause(currentNode);
      if (currentNode.equals(nextNode)) {
        nextNode = null;
      }
    }
    builder.addExceptions(exception.build());
    if (arguments != null) {
      addArguments(builder, hash, arguments);
    }
    builder.setHashId(hashToString(hash));
    builder.setIssueHashId(hashToString(issue));
    return builder.build();
  }

  private ExceptionData.Builder constructException(
      Throwable throwable, String id, MessageDigest hash, MessageDigest issue) {
    calculateInstanceHash(throwable, hash);
    calculateIssueHash(throwable, issue);
    ExceptionData.Builder builder = ExceptionData.newBuilder();
    builder.setId(id);
    if (!isNullOrEmpty(throwable.getMessage())) {
      builder.setMesssage(throwable.getMessage());
    }
    builder.setType(throwable.getClass().getName());
    builder.setStack(constructStackTrace(throwable.getStackTrace()));
    return builder;
  }

  private StackTrace constructStackTrace(StackTraceElement[] elements) {
    StackTrace.Builder builder = StackTrace.newBuilder();
    MessageDigest hash = newMessageDigest();
    if (elements.length > maxStackTraceLength) {
      for (int i = 0; i < maxStackTraceLength; i++) {
        builder.addFrames(convertStackTraceElement2StackFrame(elements[i]));
        hash.update(elements[i].toString().getBytes(UTF_8));
      }
      builder.setDroppedFramesCount(elements.length - maxStackTraceLength);
    } else {
      for (int i = 0; i < elements.length; i++) {
        builder.addFrames(convertStackTraceElement2StackFrame(elements[i]));
        hash.update(elements[i].toString().getBytes(UTF_8));
      }
    }
    builder.setStackTraceHashId(hashToString(hash));
    return builder.build();
  }

  private static String generateId() {
    return UUID.randomUUID().toString();
  }

  private static void addArguments(
      ErrorData.Builder builder, MessageDigest hash, Map<String, Object> arguments) {
    for (Map.Entry<String, Object> entry : arguments.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      hash.update(entry.getKey().getBytes(UTF_8));
      if (entry.getValue() instanceof Number) {
        Number number = (Number) entry.getValue();
        if (number instanceof Double || number instanceof Float || number instanceof BigDecimal) {
          builder.addArguments(buildDoubleAttribute(entry.getKey(), number.doubleValue()));
          ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
          buffer.putDouble(number.doubleValue());
          hash.update(buffer);
        } else {
          builder.addArguments(buildIntAttribute(entry.getKey(), number.longValue()));
          ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
          buffer.putLong(number.longValue());
          hash.update(buffer);
        }
      } else if (entry.getValue() instanceof Boolean) {
        Boolean value = (Boolean) entry.getValue();
        builder.addArguments(buildBoolAttribute(entry.getKey(), value.booleanValue()));
        hash.update((byte) (value.booleanValue() ? 1 : 0));
      } else {
        String value = entry.getValue().toString();
        builder.addArguments(buildStringAttribute(entry.getKey(), value));
        hash.update(value.getBytes(UTF_8));
      }
    }
  }

  private static AttributeKeyValue buildStringAttribute(String key, String value) {
    return AttributeKeyValue.newBuilder().setKey(key).setStringValue(value).build();
  }

  private static AttributeKeyValue buildIntAttribute(String key, long value) {
    return AttributeKeyValue.newBuilder().setKey(key).setIntValue(value).build();
  }

  private static AttributeKeyValue buildDoubleAttribute(String key, double value) {
    return AttributeKeyValue.newBuilder().setKey(key).setDoubleValue(value).build();
  }

  private static AttributeKeyValue buildBoolAttribute(String key, boolean value) {
    return AttributeKeyValue.newBuilder().setKey(key).setBoolValue(value).build();
  }

  private static AttributeKeyValue buildAnyAttribute(String key, GeneratedMessageV3 value) {
    String typeUrl = "type.googleapis.com/" + value.getDescriptorForType().getFullName();
    Any any = Any.newBuilder().setTypeUrl(typeUrl).setValue(value.toByteString()).build();
    return AttributeKeyValue.newBuilder()
        .setKey(key).setAnyValue(any).setType(ValueType.ANY).build();
  }

  private static Throwable extractCause(Throwable throwable) {
    if (throwable instanceof InvocationTargetException) {
      return ((InvocationTargetException) throwable).getTargetException();
    } else {
      Throwable cause = throwable.getCause();
      return cause == throwable ? null : cause;
    }
  }

  private static void calculateInstanceHash(Throwable throwable, MessageDigest hash) {
    hash.update(throwable.getClass().getName().getBytes(UTF_8));
    hash.update(throwable.getStackTrace()[0].toString().getBytes(UTF_8));
    if (!isNullOrEmpty(throwable.getMessage())) {
      hash.update(throwable.getMessage().getBytes(UTF_8));
    }
  }

  private static void calculateIssueHash(Throwable throwable, MessageDigest hash) {
    hash.update(throwable.getClass().getName().getBytes(UTF_8));
    hash.update(throwable.getStackTrace()[0].toString().getBytes(UTF_8));
  }

  private static MessageDigest newMessageDigest() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException willNeverBeThrown) {
      LOGGER.log(Level.INFO, "unexpected exception", willNeverBeThrown);
      throw new IllegalStateException(willNeverBeThrown);
    }
  }

  private static String hashToString(MessageDigest hash) {
    byte[] messageDigest = hash.digest();
    BigInteger no = new BigInteger(1, messageDigest);
    return padStart(no.toString(16), 32, '0');
  }

}
