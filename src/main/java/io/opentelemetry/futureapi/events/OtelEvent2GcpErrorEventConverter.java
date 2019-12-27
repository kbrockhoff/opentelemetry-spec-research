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
import static io.opentelemetry.futureapi.events.AttributeUtils.convertStackFrame2StackTraceElement;
import static io.opentelemetry.futureapi.events.EventConstants.ATTR_ERROR_OBJECT;
import static io.opentelemetry.futureapi.events.EventConstants.EVENT_ERROR;

import com.google.devtools.clouderrorreporting.v1beta1.ErrorContext;
import com.google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent;
import com.google.devtools.clouderrorreporting.v1beta1.ServiceContext;
import com.google.devtools.clouderrorreporting.v1beta1.SourceLocation;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import io.opentelemetry.proto.events.v1.ErrorData;
import io.opentelemetry.proto.events.v1.Event;
import io.opentelemetry.proto.events.v1.ExceptionData;
import io.opentelemetry.proto.events.v1.StackTrace.StackFrame;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Converts an OpenTelemetry event containing error info into a Google Cloud error event.
 */
public class OtelEvent2GcpErrorEventConverter {

  private static final Logger LOGGER =
      Logger.getLogger(OtelEvent2AwsXrayCauseConverter.class.getName());

  @Nullable
  public ReportedErrorEvent convert(Event source) {
    checkNotNull(source, "source is required");
    if (!EVENT_ERROR.equals(source.getDescription())) {
      LOGGER.info("converter only supports events of type \"error\"");
      return null;
    }
    return doConvert(source);
  }

  private ReportedErrorEvent doConvert(Event source) {
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
    ExceptionData exceptionData = errorData.getExceptions(0);
    StackFrame stackFrame;
    if (exceptionData.getStack() != null && !exceptionData.getStack().getFramesList().isEmpty()) {
      stackFrame = exceptionData.getStack().getFramesList().get(0);
    } else {
      stackFrame = StackFrame.newBuilder()
          .setLoadModule("Unknown")
          .setFunctionName("unknown")
          .setFileName("Unknown.java")
          .build();
    }
    Timestamp timestamp = Timestamp.newBuilder()
        .setSeconds(source.getTimeUnixnano() / 1000000000L)
        .setNanos((int) (source.getTimeUnixnano() % 1000000000L))
        .build();
    ServiceContext serviceContext = ServiceContext.newBuilder()
        .setService("unknown")
        .setVersion("0.x")
        .build();
    SourceLocation location = SourceLocation.newBuilder()
        .setFilePath(stackFrame.getFileName())
        .setFunctionName(stackFrame.getFunctionName())
        .setLineNumber((int) stackFrame.getLineNumber())
        .build();
    ErrorContext context = ErrorContext.newBuilder()
        .setReportLocation(location)
        .build();
    StringBuilder message = new StringBuilder();
    message.append(stackFrame.getLoadModule()).append(" ")
        .append(stackFrame.getFunctionName()).append("\n");
    message.append("Error: ").append(exceptionData.getMesssage()).append("\n");
    int index = 0;
    for (ExceptionData exception : errorData.getExceptionsList()) {
      if (index > 0) {
        message.append("Caused by: ");
      }
      message.append(exception.getType()).append(": ").append(exception.getMesssage()).append("\n");
      for (StackFrame frame : exception.getStack().getFramesList()) {
        message.append("\tat ").append(convertStackFrame2StackTraceElement(frame)).append("\n");
      }
      if (exception.getStack().getDroppedFramesCount() > 0) {
        message.append("\t... ").append(exception.getStack().getDroppedFramesCount())
            .append(" more\n");
      }
      index++;
    }

    return ReportedErrorEvent.newBuilder()
        .setMessage(message.toString())
        .setEventTime(timestamp)
        .setServiceContext(serviceContext)
        .setContext(context)
        .build();
  }

}
