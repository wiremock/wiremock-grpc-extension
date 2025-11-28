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

import static com.github.tomakehurst.wiremock.common.Pair.pair;

import com.github.tomakehurst.wiremock.common.Pair;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.TypeRegistry;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerBuilder;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.grpc.stub.ServerCalls;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class GrpcUtils {
  public static final String GRPC_STATUS_NAME = "grpc-status-name";
  public static final String GRPC_STATUS_REASON = "grpc-status-reason";

  public static <T extends ServerBuilder<T>> T buildAndBindServices(
      T serverBuilder,
      List<Descriptors.FileDescriptor> fileDescriptors,
      StubRequestHandler stubRequestHandler,
      List<ServerInterceptor> interceptors,
      Supplier<ServerAddress> serverAddressSupplier) {
    List<BindableService> services =
        buildServices(fileDescriptors, stubRequestHandler, serverAddressSupplier);
    final HeaderCopyingServerInterceptor headerCopyingServerInterceptor =
        new HeaderCopyingServerInterceptor();
    services.forEach(
        service ->
            serverBuilder.addService(
                ServerInterceptors.intercept(
                    ServerInterceptors.intercept(service, headerCopyingServerInterceptor),
                    interceptors)));
    return serverBuilder;
  }

  private static List<BindableService> buildServices(
      List<Descriptors.FileDescriptor> fileDescriptors,
      StubRequestHandler stubRequestHandler,
      Supplier<ServerAddress> serverAddressSupplier) {
    final TypeRegistry.Builder typeRegistryBuilder = TypeRegistry.newBuilder();
    fileDescriptors.forEach(
        fileDescriptor -> fileDescriptor.getMessageTypes().forEach(typeRegistryBuilder::add));
    final TypeRegistry typeRegistry = typeRegistryBuilder.build();
    JsonMessageConverter jsonMessageConverter = new JsonMessageConverter(typeRegistry);

    final Stream<BindableService> servicesFromDescriptors =
        fileDescriptors.stream()
            .flatMap(
                fileDescriptor ->
                    fileDescriptor.getServices().stream()
                        .map(service -> pair(fileDescriptor, service)))
            .map(
                fileAndServiceDescriptor ->
                    () -> {
                      final Descriptors.FileDescriptor fileDescriptor = fileAndServiceDescriptor.a;
                      final Descriptors.ServiceDescriptor serviceDescriptor =
                          fileAndServiceDescriptor.b;
                      final ServiceDescriptor.Builder serviceDescriptorBuilder =
                          ServiceDescriptor.newBuilder(serviceDescriptor.getFullName())
                              .setSchemaDescriptor(
                                  new ProtoServiceDescriptorSupplier() {

                                    @Override
                                    public Descriptors.FileDescriptor getFileDescriptor() {
                                      return fileDescriptor;
                                    }

                                    @Override
                                    public Descriptors.ServiceDescriptor getServiceDescriptor() {
                                      return serviceDescriptor;
                                    }
                                  });

                      final List<
                              Pair<
                                  MethodDescriptor<DynamicMessage, DynamicMessage>,
                                  ServerCallHandler<DynamicMessage, DynamicMessage>>>
                          methodDescriptorHandlerPairs =
                              serviceDescriptor.getMethods().stream()
                                  .map(
                                      methodDescriptor ->
                                          pair(
                                              buildMessageDescriptorInstance(
                                                  serviceDescriptor, methodDescriptor),
                                              buildHandler(
                                                  stubRequestHandler,
                                                  serviceDescriptor,
                                                  methodDescriptor,
                                                  jsonMessageConverter,
                                                  serverAddressSupplier)))
                                  .toList();

                      methodDescriptorHandlerPairs.stream()
                          .map(pair -> pair.a)
                          .forEach(serviceDescriptorBuilder::addMethod);

                      final ServerServiceDefinition.Builder builder =
                          ServerServiceDefinition.builder(serviceDescriptorBuilder.build());

                      methodDescriptorHandlerPairs.forEach(
                          pair -> builder.addMethod(pair.a, pair.b));

                      return builder.build();
                    });

    final BindableService reflectionService = ProtoReflectionServiceV1.newInstance();
    return Stream.concat(servicesFromDescriptors, Stream.of(reflectionService)).toList();
  }

  private static ServerCallHandler<DynamicMessage, DynamicMessage> buildHandler(
      StubRequestHandler stubRequestHandler,
      Descriptors.ServiceDescriptor serviceDescriptor,
      Descriptors.MethodDescriptor methodDescriptor,
      JsonMessageConverter jsonMessageConverter,
      Supplier<ServerAddress> serverAddressSupplier) {
    return methodDescriptor.isClientStreaming()
        ? ServerCalls.asyncClientStreamingCall(
            new ClientStreamingServerCallHandler(
                stubRequestHandler,
                serviceDescriptor,
                methodDescriptor,
                jsonMessageConverter,
                serverAddressSupplier))
        : ServerCalls.asyncUnaryCall(
            new UnaryServerCallHandler(
                stubRequestHandler,
                serviceDescriptor,
                methodDescriptor,
                jsonMessageConverter,
                serverAddressSupplier));
  }

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
