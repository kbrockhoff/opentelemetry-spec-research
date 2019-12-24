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
import com.rollbar.notifier.wrapper.RollbarThrowableWrapper;
import com.rollbar.notifier.wrapper.ThrowableWrapper;
import io.opentelemetry.proto.events.v1.ErrorData;
import io.opentelemetry.proto.events.v1.Event;
import io.opentelemetry.proto.events.v1.ExceptionData;
import io.opentelemetry.proto.events.v1.StackTrace;
import io.opentelemetry.proto.events.v1.StackTrace.StackFrame;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Converts an OpenTelemetry event containing error info into a Rollbar throwable wrapper.
 */
public class OtelEvent2RollbarThrowableWrapperConverter {

  private static final Logger LOGGER =
      Logger.getLogger(OtelEvent2RollbarThrowableWrapperConverter.class.getName());

  @Nullable
  public ThrowableWrapper convert(Event source) {
    checkNotNull(source, "source is required");
    if (!EVENT_ERROR.equals(source.getDescription())) {
      LOGGER.info("converter only supports events of type \"error\"");
      return null;
    }
    return doConvert(source);
  }

  private ThrowableWrapper doConvert(Event source) {
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
    RollbarThrowableWrapper target = null;
    for (int i = errorData.getExceptionsCount() - 1; i >= 0; i--) {
      ExceptionData exceptionData = errorData.getExceptions(i);
      target = new RollbarThrowableWrapper(exceptionData.getType(), exceptionData.getMesssage(),
          convertStackTrace(exceptionData.getStack()), target);
    }
    return target;
  }

  private StackTraceElement[] convertStackTrace(StackTrace source) {
    if (source == null || source.getFramesList().isEmpty()) {
      return null;
    }
    StackTraceElement[] target = new StackTraceElement[source.getFramesList().size()];
    for (int i = 0; i < target.length; i++) {
      StackFrame frame = source.getFramesList().get(i);
      target[i] = new StackTraceElement(frame.getLoadModule(), frame.getFunctionName(),
          frame.getFileName(), (int) frame.getLineNumber());
    }
    return target;
  }

}
