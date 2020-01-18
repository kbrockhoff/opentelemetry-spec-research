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

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.junit.Assert.assertEquals;

import com.google.cloud.logging.LoggingHandler;
import com.google.cloud.logging.LoggingOptions;
import com.google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent;
import io.opentelemetry.proto.events.v1.Event;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Test;

/** Unit tests for {@link OtelEvent2GcpErrorEventConverter}. */
public class OtelEvent2GcpErrorEventConverterTest extends AbstractConverterEquivalencyTesting {

  @Test
  public void shouldConvertToGcpWithTheSameDataAsIfSubmittedToGcpDirectly() throws IOException {
    Exception throwable = generateMultiCauseException();
    Event source = translateThrowableToOtelEvent(throwable);
    OtelEvent2GcpErrorEventConverter converter = new OtelEvent2GcpErrorEventConverter();
    ReportedErrorEvent target = converter.convert(source);
    LoggingOptions loggingOptions = LoggingOptions.newBuilder()
        .setProjectId("otel-research-9999999997731")
        .build();
    LoggingHandler gcpHandler = new LoggingHandler("java.log", loggingOptions);
    LogRecord logRecord = new LogRecord(Level.WARNING, throwable.getLocalizedMessage());
    logRecord.setLoggerName(OtelEvent2GcpErrorEventConverterTest.class.getName());
    logRecord.setSourceClassName(OtelEvent2GcpErrorEventConverterTest.class.getName());
    logRecord.setSourceMethodName("shouldConvertToGcpWithTheSameDataAsIfSubmittedToGcpDirectly");
    logRecord.setThrown(throwable);
    String directMessage = gcpHandler.getFormatter().format(logRecord);
    ReportedErrorEvent direct = ReportedErrorEvent.newBuilder()
        .setEventTime(target.getEventTime())
        .setServiceContext(target.getServiceContext())
        .setMessage(directMessage)
        .build();
    List<String> expectedLines = extractExceptionLines(direct.getMessage());
    List<String> actualLines = extractExceptionLines(target.getMessage());
    assertEquals(expectedLines, actualLines);
  }

  private List<String> extractExceptionLines(String message) throws IOException {
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new StringReader(message))) {
      String line = reader.readLine();
      line = reader.readLine();
      line = reader.readLine();
      while (line != null) {
        if (!isNullOrEmpty(line) && !line.startsWith("\t")) {
          lines.add(line);
        }
        line = reader.readLine();
      }
    }
    return lines;
  }
}