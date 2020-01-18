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
import static io.opentelemetry.futureapi.events.AttributeUtils.convertAttributeListToMap;
import static io.opentelemetry.futureapi.events.EventConstants.ATTR_ERROR_OBJECT;
import static io.opentelemetry.futureapi.events.EventConstants.EVENT_ERROR;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.proto.events.v1.ErrorData;
import io.opentelemetry.proto.events.v1.Event;
import io.opentelemetry.proto.events.v1.ExceptionData;
import io.opentelemetry.proto.events.v1.StackTrace;
import io.opentelemetry.proto.events.v1.StackTrace.StackFrame;
import io.sentry.event.Event.Level;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.SentryStackTraceElement;
import io.sentry.event.interfaces.StackTraceInterface;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Converts an OpenTelemetry event containing error info into a Sentry event.
 */
public class OtelEvent2SentryEventConverter {

  private static final Logger LOGGER =
      Logger.getLogger(OtelEvent2SentryEventConverter.class.getName());

  @Nullable
  public io.sentry.event.Event convert(Event source) {
    checkNotNull(source, "source is required");
    if (!EVENT_ERROR.equals(source.getDescription())) {
      LOGGER.info("converter only supports events of type \"error\"");
      return null;
    }
    return doConvert(source);
  }

  private io.sentry.event.Event doConvert(Event source) {
    Map<String, Object> attributeMap = convertAttributeListToMap(source.getAttributesList());
    Any any = (Any) attributeMap.get(ATTR_ERROR_OBJECT);
    if (any == null) {
      return null;
    }
    ErrorData errorData = null;
    try {
      errorData = any.unpack(ErrorData.class);
    } catch (InvalidProtocolBufferException cause) {
      LOGGER.log(java.util.logging.Level.WARNING, cause.getMessage(), cause);
    }
    if (errorData == null || errorData.getExceptionsList().isEmpty()) {
      return null;
    }
    String message = errorData.getExceptions(0).getMesssage();
    Deque<SentryException> exceptions = new LinkedList<>();
    for (ExceptionData exception : errorData.getExceptionsList()) {
      SentryException sentryException = convertException(exception);
      exceptions.add(sentryException);
    }
    return new io.sentry.event.EventBuilder()
        .withSdkIntegration("otel")
        .withTimestamp(new Date(source.getTimeUnixnano() / 1000000L))
        .withMessage(message)
        .withLevel(Level.ERROR)
        .withSentryInterface(new ExceptionInterface(exceptions))
        .build();
  }

  private SentryException convertException(ExceptionData source) {
    StackTraceInterface stackTraceInterface = convertStackTrace(source.getStack());
    int pos = source.getType().lastIndexOf('.');
    String exceptionClassName = source.getType().substring(pos + 1);
    String exceptionPackageName = source.getType().substring(0, pos);
    return new SentryException(source.getMesssage(), exceptionClassName, exceptionPackageName,
        stackTraceInterface);
  }

  private StackTraceInterface convertStackTrace(StackTrace source) {
    SentryStackTraceElement[] target =
        new SentryStackTraceElement[source.getFramesList().size()];
    for (int i = 0; i < target.length; i++) {
      StackFrame frame = source.getFrames(i);
      target[i] = new SentryStackTraceElement(
          frame.getLoadModule(),
          frame.getFunctionName(),
          frame.getFileName(),
          (int) frame.getLineNumber(),
          (int) frame.getColumnNumber(),
          null,
          null,
          null);
    }
    return new StackTraceInterface(target);
  }
}
