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
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;

public class GrpcUtils {
  public static final String GRPC_STATUS_NAME = "grpc-status-name";
  public static final String GRPC_STATUS_REASON = "grpc-status-reason";

  public static MethodDescriptor<DynamicMessage, DynamicMessage> buildMessageDescriptorInstance(
      Descriptors.ServiceDescriptor serviceDescriptor,
      Descriptors.MethodDescriptor methodDescriptor) {
    return MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
        .setType(getMethodTypeFromDesc(methodDescriptor))
        .setFullMethodName(
            MethodDescriptor.generateFullMethodName(
                serviceDescriptor.getFullName(), methodDescriptor.getName()))
        .setRequestMarshaller(
            ProtoUtils.marshaller(
                DynamicMessage.getDefaultInstance(methodDescriptor.getInputType())))
        .setResponseMarshaller(
            ProtoUtils.marshaller(
                DynamicMessage.getDefaultInstance(methodDescriptor.getOutputType())))
        .build();
  }

  public static MethodDescriptor.MethodType getMethodTypeFromDesc(
      Descriptors.MethodDescriptor methodDesc) {
    if (!methodDesc.isServerStreaming() && !methodDesc.isClientStreaming()) {
      return MethodDescriptor.MethodType.UNARY;
    } else if (methodDesc.isServerStreaming() && !methodDesc.isClientStreaming()) {
      return MethodDescriptor.MethodType.SERVER_STREAMING;
    } else if (!methodDesc.isServerStreaming()) {
      return MethodDescriptor.MethodType.CLIENT_STREAMING;
    } else {
      return MethodDescriptor.MethodType.BIDI_STREAMING;
    }
  }
}
