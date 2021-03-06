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

import static org.junit.Assert.assertEquals;

import io.opentelemetry.proto.events.v1.Event;
import io.sentry.event.Event.Level;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.SentryStackTraceElement;
import java.util.Date;
import java.util.Iterator;
import org.junit.Test;

/** Unit tests for {@link OtelEvent2SentryEventConverter}. */
public class OtelEvent2SentryEventConverterTest extends AbstractConverterEquivalencyTesting {

  @Test
  public void shouldConvertToSentryWithTheSameDataAsIfSubmittedToSentryDirectly() {
    Exception throwable = generateMultiCauseException();
    Event source = translateThrowableToOtelEvent(throwable);
    OtelEvent2SentryEventConverter converter = new OtelEvent2SentryEventConverter();
    io.sentry.event.Event target = converter.convert(source);
    io.sentry.event.Event direct = new EventBuilder()
        .withSdkIntegration("otel")
        .withTimestamp(new Date())
        .withMessage(throwable.getMessage())
        .withLevel(Level.ERROR)
        .withSentryInterface(new ExceptionInterface(throwable))
        .build();
    assertEquals(direct.getMessage(), target.getMessage());
    assertEquals(direct.getSentryInterfaces().size(), target.getSentryInterfaces().size());
    ExceptionInterface directException = (ExceptionInterface)
        direct.getSentryInterfaces().get("sentry.interfaces.Exception");
    ExceptionInterface targetException = (ExceptionInterface)
        target.getSentryInterfaces().get("sentry.interfaces.Exception");
    assertEquals(directException.getExceptions().size(), targetException.getExceptions().size());
    Iterator<SentryException> directIter = directException.getExceptions().iterator();
    Iterator<SentryException> targetIter = targetException.getExceptions().iterator();
    while (directIter.hasNext()) {
      SentryException expected = directIter.next();
      SentryException actual = targetIter.next();
      assertEquals(expected.getExceptionMessage(), actual.getExceptionMessage());
      assertEquals(expected.getExceptionClassName(), actual.getExceptionClassName());
      assertEquals(expected.getExceptionPackageName(), actual.getExceptionPackageName());
      assertEquals(expected.getStackTraceInterface().getStackTrace().length,
          actual.getStackTraceInterface().getStackTrace().length);
      SentryStackTraceElement expectedElement =
          expected.getStackTraceInterface().getStackTrace()[0];
      SentryStackTraceElement actualElement = actual.getStackTraceInterface().getStackTrace()[0];
      assertEquals(expectedElement.getFileName(), actualElement.getFileName());
      assertEquals(expectedElement.getModule(), actualElement.getModule());
      assertEquals(expectedElement.getFunction(), actualElement.getFunction());
      assertEquals(expectedElement.getLineno(), actualElement.getLineno());
    }
  }

}