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

public final class EventConstants {

  public static final String EVENT_ERROR = "error";
  public static final String ATTR_ERROR_KIND = "error.kind";
  public static final String ATTR_ERROR_OBJECT = "error.object";
  public static final String ATTR_ERROR_MESSAGE = "error.message";
  public static final String ATTR_ERROR_STACK = "error.stack";

  private EventConstants() {}
}
