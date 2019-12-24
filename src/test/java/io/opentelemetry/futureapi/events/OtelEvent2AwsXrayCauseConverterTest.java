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

import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Cause;
import com.amazonaws.xray.entities.SegmentImpl;
import com.amazonaws.xray.entities.ThrowableDescription;
import com.amazonaws.xray.strategy.DefaultThrowableSerializationStrategy;
import io.opentelemetry.proto.events.v1.Event;
import java.util.Iterator;
import org.junit.Test;

/** Unit tests for {@link OtelEvent2AwsXrayCauseConverter}. */
public class OtelEvent2AwsXrayCauseConverterTest extends AbstractConverterEquivalencyTesting {

  @Test
  public void shouldConvertToXrayWithTheSameDataAsIfSubmittedToXrayDirectly() {
    Exception throwable = generateMultiCauseException();
    Event source = translateThrowableToOtelEvent(throwable);
    OtelEvent2AwsXrayCauseConverter converter = new OtelEvent2AwsXrayCauseConverter();
    Cause target = converter.convert(source);
    AWSXRayRecorder creator = new AWSXRayRecorder();
    creator.setThrowableSerializationStrategy(
        new DefaultThrowableSerializationStrategy(128));
    SegmentImpl segment = new SegmentImpl(creator, "junit");
    segment.addException(throwable);
    Cause direct = segment.getCause();
    assertEquals(direct.getExceptions().size(), target.getExceptions().size());
    Iterator<ThrowableDescription> directIter = direct.getExceptions().iterator();
    Iterator<ThrowableDescription> targetIter = target.getExceptions().iterator();
    while (directIter.hasNext()) {
      ThrowableDescription expected = directIter.next();
      ThrowableDescription actual = targetIter.next();
      assertEquals(expected.getMessage(), actual.getMessage());
      assertEquals(expected.getType(), actual.getType());
      assertEquals(expected.getStack().length, actual.getStack().length);
      StackTraceElement expectedElement = expected.getStack()[0];
      StackTraceElement actualElement = actual.getStack()[0];
      assertEquals(expectedElement.getFileName(), actualElement.getFileName());
      assertEquals(expectedElement.getClassName(), actualElement.getClassName());
      assertEquals(expectedElement.getMethodName(), actualElement.getMethodName());
      assertEquals(expectedElement.getLineNumber(), actualElement.getLineNumber());
    }
  }
}