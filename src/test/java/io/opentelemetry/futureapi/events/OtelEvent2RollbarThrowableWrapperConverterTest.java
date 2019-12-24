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

import com.rollbar.notifier.wrapper.RollbarThrowableWrapper;
import com.rollbar.notifier.wrapper.ThrowableWrapper;
import io.opentelemetry.proto.events.v1.Event;
import org.junit.Test;

/** Unit tests for {@link OtelEvent2RollbarThrowableWrapperConverter}. */
public class OtelEvent2RollbarThrowableWrapperConverterTest
    extends AbstractConverterEquivalencyTesting {

  @Test
  public void shouldConvertToRollbarWithTheSameDataAsIfSubmittedToRollbarDirectly() {
    Exception throwable = generateMultiCauseException();
    Event source = translateThrowableToOtelEvent(throwable);
    OtelEvent2RollbarThrowableWrapperConverter converter =
        new OtelEvent2RollbarThrowableWrapperConverter();
    ThrowableWrapper target = converter.convert(source);
    ThrowableWrapper direct = new RollbarThrowableWrapper(throwable);
    assertEquals(direct.getMessage(), target.getMessage());
    assertEquals(direct.getClassName(), target.getClassName());
    assertEquals(direct.getStackTrace().length, target.getStackTrace().length);
    assertEquals(direct.getCause().getMessage(), target.getCause().getMessage());
    assertEquals(direct.getCause().getClassName(), target.getCause().getClassName());
    assertEquals(direct.getCause().getStackTrace().length, target.getCause().getStackTrace().length);
    StackTraceElement expectedElement = direct.getStackTrace()[0];
    StackTraceElement actualElement = target.getStackTrace()[0];
    assertEquals(expectedElement.getFileName(), actualElement.getFileName());
    assertEquals(expectedElement.getClassName(), actualElement.getClassName());
    assertEquals(expectedElement.getMethodName(), actualElement.getMethodName());
    assertEquals(expectedElement.getLineNumber(), actualElement.getLineNumber());
  }
}