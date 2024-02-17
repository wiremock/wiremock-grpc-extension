/*
 * Copyright (C) 2023-2024 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wiremock.grpc.internal;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TypeRegistry;

public class JsonMessageUtils {

  private static final JsonMessageConverter converter =
      new JsonMessageConverter(TypeRegistry.getEmptyTypeRegistry());

  private JsonMessageUtils() {}

  public static String toJson(MessageOrBuilder message) {
    return converter.toJson(message);
  }

  public static <T extends Message, B extends Message.Builder> T toMessage(String json, B builder) {
    return converter.toMessage(json, builder);
  }
}
