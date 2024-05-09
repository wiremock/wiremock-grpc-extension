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

import com.github.tomakehurst.wiremock.common.Exceptions;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.HttpServerFactory;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty11.Jetty11HttpServer;
import com.github.tomakehurst.wiremock.store.BlobStore;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GrpcHttpServerFactory implements HttpServerFactory {

  private final List<Descriptors.FileDescriptor> fileDescriptors = new ArrayList<>();

  public GrpcHttpServerFactory(BlobStore protoDescriptorStore) {
    List<DescriptorProtos.FileDescriptorProto> descriptorProtos = protoDescriptorStore
            .getAllKeys()
            .filter(key -> key.endsWith(".dsc") || key.endsWith(".desc"))
            .map(
                    key ->
                            protoDescriptorStore
                                    .get(key)
                                    .map(
                                            data ->
                                                    Exceptions.uncheck(
                                                            () -> DescriptorProtos.FileDescriptorSet.parseFrom(data),
                                                            DescriptorProtos.FileDescriptorSet.class)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(fileDescriptorSet -> fileDescriptorSet.getFileList().stream())
            .collect(Collectors.toList());

    convertToFileDescriptors(descriptorProtos);
  }

  public GrpcHttpServerFactory(List<URL> protoUrls) {
    List<DescriptorProtos.FileDescriptorProto> protos = protoUrls.stream()
            .filter(key -> key.getPath().endsWith(".dsc") || key.getPath().endsWith(".desc"))
            .map(
                    data ->
                            Exceptions.uncheck(
                                    () -> DescriptorProtos.FileDescriptorSet.parseFrom(data.openStream()),
                                    DescriptorProtos.FileDescriptorSet.class))
            .flatMap(fileDescriptorSet -> fileDescriptorSet.getFileList().stream())
            .collect(Collectors.toList());

    convertToFileDescriptors(protos);
  }

  private void convertToFileDescriptors(List<DescriptorProtos.FileDescriptorProto> protos){
    protos
            .forEach(
                    fileDescriptorProto ->
                            Exceptions.uncheck(() ->
                                    fileDescriptors.add(
                                            Descriptors.FileDescriptor.buildFrom(
                                                    fileDescriptorProto,
                                                    fileDescriptors.toArray(Descriptors.FileDescriptor[]::new),
                                                    true))));
  }

  @Override
  public String getName() {
    return "grpc";
  }

  @Override
  public HttpServer buildHttpServer(
      Options options,
      AdminRequestHandler adminRequestHandler,
      StubRequestHandler stubRequestHandler) {
    return new Jetty11HttpServer(options, adminRequestHandler, stubRequestHandler) {
      @Override
      protected void decorateMockServiceContextBeforeConfig(
          ServletContextHandler mockServiceContext) {

        final GrpcFilter grpcFilter = new GrpcFilter(stubRequestHandler, fileDescriptors);
        final FilterHolder filterHolder = new FilterHolder(grpcFilter);
        mockServiceContext.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
      }
    };
  }
}
