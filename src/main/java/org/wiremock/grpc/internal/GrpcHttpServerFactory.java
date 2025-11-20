/*
 * Copyright (C) 2023-2025 Thomas Akehurst
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

import com.github.tomakehurst.wiremock.common.JettySettings;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.jetty12.Jetty12HttpServer;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import java.util.Objects;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class GrpcHttpServerFactory implements HttpServerFactory, GrpcResetAdminApiTask {

  private final JettySettings jettySettings;
  private final ProtoDescriptorStore protoDescriptorStore;
  protected GrpcFilter grpcFilter;

  public GrpcHttpServerFactory(ProtoDescriptorStore protoDescriptorStore) {
    this(protoDescriptorStore, null);
  }

  public GrpcHttpServerFactory(
      ProtoDescriptorStore protoDescriptorStore, JettySettings jettySettings) {
    Objects.requireNonNull(protoDescriptorStore, "protoDescriptorStore cannot be null");
    this.protoDescriptorStore = protoDescriptorStore;
    this.jettySettings =
        jettySettings != null ? jettySettings : JettySettings.Builder.aJettySettings().build();
  }

  @Override
  public void loadFileDescriptors() {
    grpcFilter.loadFileDescriptors(protoDescriptorStore.loadAllFileDescriptors());
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
    return new Jetty12HttpServer(
        options,
        adminRequestHandler,
        stubRequestHandler,
        jettySettings,
        new QueuedThreadPool(options.containerThreads())) {
      @Override
      protected void decorateMockServiceContextBeforeConfig(
          ServletContextHandler mockServiceContext) {

        grpcFilter = new GrpcFilter(stubRequestHandler);
        loadFileDescriptors();
        final FilterHolder filterHolder = new FilterHolder(grpcFilter);
        mockServiceContext.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
      }
    };
  }
}
