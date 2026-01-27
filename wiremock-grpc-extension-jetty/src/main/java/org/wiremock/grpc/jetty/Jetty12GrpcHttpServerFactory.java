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
package org.wiremock.grpc.jetty;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.jetty.Jetty12HttpServer;
import com.github.tomakehurst.wiremock.jetty.JettySettings;
import com.github.tomakehurst.wiremock.message.MessageStubRequestHandler;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import java.util.Objects;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.wiremock.grpc.GrpcHttpServerFactory;
import org.wiremock.grpc.internal.ProtoDescriptorStore;

public class Jetty12GrpcHttpServerFactory implements GrpcHttpServerFactory {

  private final JettySettings jettySettings;
  private final Notifier reloadNotifier = new Notifier();
  private ProtoDescriptorStore protoDescriptorStore;

  public Jetty12GrpcHttpServerFactory() {
    this(null);
  }

  public Jetty12GrpcHttpServerFactory(JettySettings jettySettings) {
    this.jettySettings =
        jettySettings != null ? jettySettings : JettySettings.Builder.aJettySettings().build();
  }

  @Override
  public void reloadFileDescriptors() {
    reloadNotifier.notifyListeners();
  }

  @Override
  public String getName() {
    return "grpc";
  }

  @Override
  public void initProtoDescriptorStore(ProtoDescriptorStore store) {
    protoDescriptorStore = Objects.requireNonNull(store, "store cannot be null");
  }

  @Override
  public HttpServer buildHttpServer(
      Options options,
      AdminRequestHandler adminRequestHandler,
      StubRequestHandler stubRequestHandler,
      MessageStubRequestHandler messageStubRequestHandler) {
    return new Jetty12HttpServer(
        options,
        adminRequestHandler,
        stubRequestHandler,
        jettySettings,
        new QueuedThreadPool(options.containerThreads()),
        messageStubRequestHandler) {
      @Override
      protected void decorateMockServiceContextBeforeConfig(
          ServletContextHandler mockServiceContext) {
        if (protoDescriptorStore == null) {
          throw new IllegalStateException(
              "Must call initProtoDescriptorStore before using the server factory");
        }

        GrpcFilter grpcFilter = new GrpcFilter(stubRequestHandler);
        Runnable loadFileDescriptors =
            () -> grpcFilter.loadFileDescriptors(protoDescriptorStore.loadAllFileDescriptors());
        reloadNotifier.addListener(loadFileDescriptors);
        loadFileDescriptors.run();
        final FilterHolder filterHolder = new FilterHolder(grpcFilter);
        mockServiceContext.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
      }
    };
  }
}
