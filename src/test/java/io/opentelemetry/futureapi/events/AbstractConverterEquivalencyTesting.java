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

import io.opentelemetry.proto.events.v1.Event;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractConverterEquivalencyTesting {

  protected static Event translateThrowableToOtelEvent(Exception throwable) {
    ThrowableTranslator translator = new ThrowableTranslator(128);
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("personId", 58763);
    parameters.put("firstName", "Kent");
    parameters.put("lastName", "Beck");
    parameters.put("averageRating", 4.87);
    parameters.put("participant", true);
    return translator.translateThrowable(throwable, parameters);
  }

  protected static Exception generateMultiCauseException() {
    try {
      AbstractConverterEquivalencyTesting.callThatThrowsNestedIllegalArgumentException();
    } catch (Exception exception) {
      return exception;
    }
    return null;
  }

  private static void callThatThrowsNestedIllegalArgumentException() {
    try {
      AbstractConverterEquivalencyTesting.callThatThrowsSQLException();
    } catch (SQLException cause) {
      throw new IllegalArgumentException("invalid input data", cause);
    }
  }

  private static void callThatThrowsSQLException() throws SQLException {
    throw new SQLIntegrityConstraintViolationException(
        "Column widget_id cannot be null", "23000", 1048);
  }
}
