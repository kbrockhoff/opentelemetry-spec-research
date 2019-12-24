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

import static io.opentelemetry.futureapi.events.EventConstants.EVENT_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.proto.events.v1.ErrorData;
import io.opentelemetry.proto.events.v1.Event;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.Test;

/** Unit tests for {@link ThrowableTranslator}. */
public class ThrowableTranslatorTest {

  private static final Logger LOGGER = Logger.getLogger(ThrowableTranslatorTest.class.getName());

  @Test
  public void shouldTranslateExceptionWithNoMessageNoNestedCausesAndNullParameters() {
    ThrowableTranslator translator = new ThrowableTranslator();
    Exception throwable = new NullPointerException();
    Event event = translator.translateThrowable(throwable, null);
    LOGGER.info(event.toByteString().toStringUtf8());
    validateEventTime(event);
    assertEquals(EVENT_ERROR, event.getDescription());
    assertEquals(2, event.getAttributesCount());
  }

  @Test
  public void shouldTranslateExceptionWithMessageNestedCauseAndParameters()
      throws InvalidProtocolBufferException {
    ThrowableTranslator translator = new ThrowableTranslator();
    Exception throwable = generateMultiCauseException();
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("personId", 58763);
    parameters.put("firstName", "Kent");
    parameters.put("lastName", "Beck");
    parameters.put("averageRating", 4.87);
    parameters.put("participant", true);
    Event event = translator.translateThrowable(throwable, parameters);
    LOGGER.info(event.toByteString().toStringUtf8());
    validateEventTime(event);
    assertEquals(EVENT_ERROR, event.getDescription());
    assertEquals(2, event.getAttributesCount());
    Any any = event.getAttributesList().get(1).getAnyValue();
    ErrorData errorData = ErrorData.parseFrom(any.getValue());
    assertEquals(2, errorData.getExceptionsCount());
    assertEquals(5, errorData.getArgumentsCount());
  }

  private void validateEventTime(Event event) {
    Instant timestamp = Instant.ofEpochSecond(event.getTimeUnixnano() / 1000000000L,
        event.getTimeUnixnano() % 1000000000L);
    Instant current = Instant.now();
    assertTrue(timestamp.getEpochSecond() <= current.getEpochSecond() &&
        timestamp.getEpochSecond() > current.getEpochSecond() - 60);
  }

  private Exception generateMultiCauseException() {
    try {
      callThatThrowsNestedIllegalArgumentException();
    } catch (Exception exception) {
      return exception;
    }
    return null;
  }

  private void callThatThrowsNestedIllegalArgumentException() {
    try {
      callThatThrowsSQLException();
    } catch (SQLException cause) {
      throw new IllegalArgumentException("invalid input data", cause);
    }
  }

  private void callThatThrowsSQLException() throws SQLException {
    throw new SQLIntegrityConstraintViolationException(
        "Column widget_id cannot be null", "23000", 1048);
  }
}