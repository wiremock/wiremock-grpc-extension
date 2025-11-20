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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.wiremock.grpc.internal.GrpcUtils.buildAndBindServices;

import com.github.tomakehurst.wiremock.common.Exceptions;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.google.protobuf.Descriptors;
import io.grpc.*;
import io.grpc.servlet.jakarta.ServletAdapter;
import io.grpc.servlet.jakarta.ServletServerBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GrpcFilter extends HttpFilter {

  //  private GrpcServlet grpcServlet;
  private ServletAdapter servletAdapter;
  private final StubRequestHandler stubRequestHandler;

  public GrpcFilter(StubRequestHandler stubRequestHandler) {
    this.stubRequestHandler = stubRequestHandler;
  }

  public void loadFileDescriptors(List<Descriptors.FileDescriptor> fileDescriptors) {
    loadFileDescriptors(fileDescriptors, Collections.emptyList());
  }

  public void loadFileDescriptors(
      List<Descriptors.FileDescriptor> fileDescriptors, List<ServerInterceptor> interceptors) {
    servletAdapter =
        buildAndBindServices(
                new ServletServerBuilder(), fileDescriptors, stubRequestHandler, interceptors)
            .buildServletAdapter();
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
