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

import static com.github.tomakehurst.wiremock.http.Response.response;

import com.github.tomakehurst.wiremock.extension.ProxyRenderer;
import com.github.tomakehurst.wiremock.global.GlobalSettings;
import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.store.SettingsStore;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.protobuf.DynamicMessage;
import io.grpc.*;
import io.grpc.stub.ClientCalls;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class GrpcResponseRenderer implements ProxyRenderer {
  private final SettingsStore settingsStore;
  private final boolean stubCorsEnabled;

  public GrpcResponseRenderer(SettingsStore settingsStore, boolean stubCorsEnabled) {
    this.settingsStore = settingsStore;
    this.stubCorsEnabled = stubCorsEnabled;
  }

  @Override
  public boolean applyFor(ServeEvent serveEvent) {
    ResponseDefinition responseDefinition = serveEvent.getResponseDefinition();
    Request originalRequest = responseDefinition.getOriginalRequest();
    return originalRequest instanceof GrpcRequest;
  }

  @Override
  public int Order() {
    return 0;
  }

  @Override
  public Response render(ServeEvent serveEvent) {
    ResponseDefinition responseDefinition = serveEvent.getResponseDefinition();
    GrpcRequest grpcRequest = (GrpcRequest) responseDefinition.getOriginalRequest();
    GlobalSettings settings = settingsStore.get();

    URI uri = URI.create(responseDefinition.getProxyBaseUrl());
    Channel channel =
        ManagedChannelBuilder.forAddress(uri.getHost(), uri.getPort()).usePlaintext().build();
    List<HttpHeader> headers = new ArrayList<>();
    headers.add(new HttpHeader("Content-Type", grpcRequest.getHeader("Content-Type")));
    Response.Builder grpcRespBuilder = response();
    String statusName = Status.Code.OK.name();
    String statusReason = null;
    try {
      DynamicMessage responseMsg =
          ClientCalls.blockingUnaryCall(
              channel,
              GrpcUtils.buildMessageDescriptorInstance(
                  grpcRequest.getServiceDescriptor(), grpcRequest.getMethodDescriptor()),
              CallOptions.DEFAULT,
              grpcRequest.getDynamicMessage());
      JsonMessageConverter converter = grpcRequest.getJsonMessageConverter();
      String jsonStr = converter.toJson(responseMsg);
      headers.add(new HttpHeader(GrpcUtils.GRPC_STATUS_NAME, statusName));
      grpcRespBuilder.status(200).body(jsonStr);
    } catch (Exception e) {
      Status grpcStatus = null;
      if (e instanceof StatusRuntimeException) {
        StatusRuntimeException stsEx = (StatusRuntimeException) e;
        grpcStatus = stsEx.getStatus();
        statusName = grpcStatus.getCode().name();
        statusReason = grpcStatus.getDescription();
      } else {
        grpcStatus = Status.INTERNAL;
        statusName = Status.Code.INTERNAL.name();
        statusReason = e.getMessage();
      }

      Integer httpStatus = GrpcStatusUtils.reverseMappings.get(grpcStatus);
      if (httpStatus != null) {
        grpcRespBuilder.status(httpStatus);
      } else {
        grpcRespBuilder.status(500);
      }
      headers.add(new HttpHeader(GrpcUtils.GRPC_STATUS_NAME, statusName));
      headers.add(new HttpHeader(GrpcUtils.GRPC_STATUS_REASON, statusReason));
    }

    Response httpResponse =
        grpcRespBuilder.headers(new HttpHeaders(headers.toArray(HttpHeader[]::new))).build();
    return Response.Builder.like(httpResponse)
        .fromProxy(true)
        .headers(HeaderUtil.headersFrom(httpResponse, responseDefinition, stubCorsEnabled))
        .configureDelay(
            settings.getFixedDelay(),
            settings.getDelayDistribution(),
            responseDefinition.getFixedDelayMilliseconds(),
            responseDefinition.getDelayDistribution())
        .chunkedDribbleDelay(responseDefinition.getChunkedDribbleDelay())
        .build();
  }
}
