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

import static org.junit.Assert.*;

import com.google.protobuf.Any;
import io.opentelemetry.proto.common.v1.AttributeKeyValue;
import io.opentelemetry.proto.common.v1.AttributeKeyValue.ValueType;
import io.opentelemetry.proto.common.v1.AttributeKeyValueOrBuilder;
import io.opentelemetry.proto.events.v1.Event;
import io.opentelemetry.proto.events.v1.StackTrace.StackFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class AttributeUtilsTest {

  @Test
  public void shouldConvertAllTypesOfAttributes() {
    List<AttributeKeyValue> attributes = new ArrayList<>();
    AttributeKeyValue strAttr = AttributeKeyValue.newBuilder()
        .setKey("strKey").setStringValue("strValue").build();
    attributes.add(strAttr);
    AttributeKeyValue intAttr = AttributeKeyValue.newBuilder()
        .setKey("intKey").setIntValue(42).build();
    attributes.add(intAttr);
    AttributeKeyValue dblAttr = AttributeKeyValue.newBuilder()
        .setKey("dblKey").setDoubleValue(3.14).build();
    attributes.add(dblAttr);
    AttributeKeyValue boolAttr = AttributeKeyValue.newBuilder()
        .setKey("boolKey").setBoolValue(false).build();
    attributes.add(boolAttr);
    AttributeKeyValue anyAttr = AttributeKeyValue.newBuilder()
        .setKey("anyKey").setAnyValue(generateErrorInfoAny()).build();
    attributes.add(anyAttr);

    Map<String, Object> results = AttributeUtils.convertAttributeListToMap(attributes);
    assertEquals(5, results.size());
  }

  @Test
  public void shouldConvertStackTraceElementToStackFrameAndBackWithEquality() {
    StackTraceElement original = new StackTraceElement(
        "java.text.SimpleDateFormat",
        "parse",
        "SimpleDateFormat.java",
        1234
    );
    StackFrame stackFrame = AttributeUtils.convertStackTraceElement2StackFrame(original);
    StackTraceElement reconverted = AttributeUtils.convertStackFrame2StackTraceElement(stackFrame);
    assertEquals(original, reconverted);
  }

  @Test
  public void shouldConvertNullStackTraceElementToNullStackFrameAndBack() {
    StackTraceElement original = null;
    StackFrame stackFrame = AttributeUtils.convertStackTraceElement2StackFrame(original);
    StackTraceElement reconverted = AttributeUtils.convertStackFrame2StackTraceElement(stackFrame);
    assertNull(reconverted);
  }


  private Any generateErrorInfoAny() {
    ThrowableTranslator translator = new ThrowableTranslator();
    Exception throwable = new NullPointerException();
    Event event = translator.translateThrowable(throwable, null);
    AttributeKeyValue errorInfo = null;
    for (AttributeKeyValue akv : event.getAttributesList()) {
      if (ValueType.ANY.equals(akv.getType())) {
        errorInfo = akv;
      }
    }
    return errorInfo.getAnyValue();
  }

}