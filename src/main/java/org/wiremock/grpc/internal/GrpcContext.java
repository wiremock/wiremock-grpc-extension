/*
 * Copyright (C) 2025 Thomas Akehurst
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

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

public class GrpcContext {
  private final Descriptors.ServiceDescriptor serviceDescriptor;
  private final Descriptors.MethodDescriptor methodDescriptor;
  private final JsonMessageConverter jsonMessageConverter;
  private final DynamicMessage dm;

  public GrpcContext(
      Descriptors.ServiceDescriptor serviceDescriptor,
      Descriptors.MethodDescriptor methodDescriptor,
      JsonMessageConverter jsonMessageConverter,
      DynamicMessage dm) {
    this.serviceDescriptor = serviceDescriptor;
    this.methodDescriptor = methodDescriptor;
    this.jsonMessageConverter = jsonMessageConverter;
    this.dm = dm;
  }

  public Descriptors.ServiceDescriptor getServiceDescriptor() {
    return serviceDescriptor;
  }

  public Descriptors.MethodDescriptor getMethodDescriptor() {
    return methodDescriptor;
  }

  public JsonMessageConverter getJsonMessageConverter() {
    return jsonMessageConverter;
  }

  public DynamicMessage getDm() {
    return dm;
  }
}
