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

import static com.github.tomakehurst.wiremock.common.Pair.pair;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.tomakehurst.wiremock.common.Exceptions;
import com.github.tomakehurst.wiremock.common.Pair;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.TypeRegistry;
import io.grpc.*;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.grpc.servlet.jakarta.ServletAdapter;
import io.grpc.servlet.jakarta.ServletServerBuilder;
import io.grpc.stub.ServerCalls;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GrpcFilter extends HttpFilter {

  //  private GrpcServlet grpcServlet;
  private ServletAdapter servletAdapter;
  private final StubRequestHandler stubRequestHandler;

  public GrpcFilter(StubRequestHandler stubRequestHandler) {
    this.stubRequestHandler = stubRequestHandler;
  }

  public void loadFileDescriptors(List<Descriptors.FileDescriptor> fileDescriptors) {
    final List<BindableService> services = buildServices(fileDescriptors);
    servletAdapter = loadServices(services);
  }

  private static ServletAdapter loadServices(List<? extends BindableService> bindableServices) {
    final HeaderCopyingServerInterceptor headerCopyingServerInterceptor =
        new HeaderCopyingServerInterceptor();
    final ServletServerBuilder serverBuilder = new ServletServerBuilder();
    bindableServices.forEach(
        service -> {
          serverBuilder.addService(
              ServerInterceptors.intercept(service, headerCopyingServerInterceptor));
        });
    return serverBuilder.buildServletAdapter();
  }

  private List<BindableService> buildServices(List<Descriptors.FileDescriptor> fileDescriptors) {
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
                                                  serviceDescriptor,
                                                  methodDescriptor,
                                                  jsonMessageConverter)))
                                  .collect(Collectors.toList());

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
    return Stream.concat(servicesFromDescriptors, Stream.of(reflectionService))
        .collect(Collectors.toUnmodifiableList());
  }

  private ServerCallHandler<DynamicMessage, DynamicMessage> buildHandler(
      Descriptors.ServiceDescriptor serviceDescriptor,
      Descriptors.MethodDescriptor methodDescriptor,
      JsonMessageConverter jsonMessageConverter) {
    return methodDescriptor.isClientStreaming()
        ? ServerCalls.asyncClientStreamingCall(
            new ClientStreamingServerCallHandler(
                stubRequestHandler, serviceDescriptor, methodDescriptor, jsonMessageConverter))
        : ServerCalls.asyncUnaryCall(
            new UnaryServerCallHandler(
                stubRequestHandler, serviceDescriptor, methodDescriptor, jsonMessageConverter));
  }

  private static MethodDescriptor<DynamicMessage, DynamicMessage> buildMessageDescriptorInstance(
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

  private static MethodDescriptor.MethodType getMethodTypeFromDesc(
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

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!ServletAdapter.isGrpc(request)) {
      chain.doFilter(request, response);
      return;
    }

    ServerAddress.set(request.getScheme(), request.getLocalAddr(), request.getLocalPort());

    final String method = request.getMethod();
    if (isPost(method)) {
      servletAdapter.doPost(request, response);
    } else if (isGet(method)) {
      servletAdapter.doGet(request, response);
    }
  }

  @Override
  public void destroy() {
    servletAdapter.destroy();
  }

  private static boolean isGet(String method) {
    return method.equalsIgnoreCase("GET");
  }

  private static boolean isPost(String method) {
    return method.equalsIgnoreCase("POST");
  }

  public static class ServerAddress {
    private static final CompletableFuture<ServerAddress> instance = new CompletableFuture<>();

    public static void set(String scheme, String hostname, int port) {
      instance.complete(new ServerAddress(scheme, hostname, port));
    }

    public static ServerAddress get() {
      return Exceptions.uncheck(() -> instance.get(5, SECONDS), ServerAddress.class);
    }

    final String scheme;
    final String hostname;
    final int port;

    public ServerAddress(String scheme, String hostname, int port) {
      this.scheme = scheme;
      this.hostname = hostname;
      this.port = port;
    }
  }
}
