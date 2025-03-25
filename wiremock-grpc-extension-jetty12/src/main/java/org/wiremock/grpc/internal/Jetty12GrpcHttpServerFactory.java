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

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty12.Jetty12HttpServer;
import com.github.tomakehurst.wiremock.store.BlobStore;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;

import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;

public class Jetty12GrpcHttpServerFactory extends GrpcHttpServerFactory {

  public Jetty12GrpcHttpServerFactory(BlobStore protoDescriptorStore) {
    super(protoDescriptorStore);
  }

  @Override
  public String getName() {
    return "grpc-jetty12";
  }

  @Override
  public HttpServer buildHttpServer(
      Options options,
      AdminRequestHandler adminRequestHandler,
      StubRequestHandler stubRequestHandler) {
    return new Jetty12HttpServer(options, adminRequestHandler, stubRequestHandler) {
      @Override
      protected void decorateMockServiceContextBeforeConfig(ServletContextHandler mockServiceContext) {
        grpcFilter = new GrpcFilter(stubRequestHandler, options.notifier());
        loadFileDescriptors();
        final FilterHolder filterHolder = new FilterHolder(grpcFilter);
        mockServiceContext.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
      }
    };
  }
}
