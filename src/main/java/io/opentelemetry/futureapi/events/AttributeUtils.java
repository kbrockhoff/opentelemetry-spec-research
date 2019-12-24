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

import io.opentelemetry.proto.common.v1.AttributeKeyValue;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides utility methods for working with OpenTelemetry attributes.
 */
public final class AttributeUtils {

  public static Map<String, Object> convertAttributeListToMap(
      Collection<AttributeKeyValue> attributes) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (attributes == null) {
      return map;
    }
    for (AttributeKeyValue akv : attributes) {
      switch (akv.getType()) {
        case STRING:
          map.put(akv.getKey(), akv.getStringValue());
          break;
        case INT:
          map.put(akv.getKey(), akv.getIntValue());
          break;
        case DOUBLE:
          map.put(akv.getKey(), akv.getDoubleValue());
          break;
        case BOOL:
          map.put(akv.getKey(), akv.getBoolValue());
          break;
        case ANY:
          map.put(akv.getKey(), akv.getAnyValue());
          break;
      }
    }
    return map;
  }


  private AttributeUtils() {}

}
