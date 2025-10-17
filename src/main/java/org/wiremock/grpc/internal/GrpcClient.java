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

import com.github.tomakehurst.wiremock.common.Pair;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.http.client.ApacheHttpClientFactory;
import com.github.tomakehurst.wiremock.http.client.HttpClient;
import com.google.protobuf.DynamicMessage;
import io.grpc.*;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GrpcClient implements HttpClient {
  private final HttpClient delegateClient;

  public GrpcClient(
      Options options,
      boolean trustAllCertificates,
      List<String> trustedHosts,
      boolean useSystemProperties) {
    this.delegateClient =
        new ApacheHttpClientFactory()
            .buildHttpClient(options, trustAllCertificates, trustedHosts, useSystemProperties);
  }

  @Override
  public Response execute(Request request) throws IOException {
    String contentType = request.getHeader("Content-Type");
    if (!"application/grpc".equalsIgnoreCase(contentType)) {
      return delegateClient.execute(request);
    }

    GrpcContext context = BaseCallHandler.CONTEXT.get();
    BaseCallHandler.CONTEXT.remove();

    ManagedChannelBuilder<?> managedChannelBuilder = ManagedChannelBuilder.forAddress(request.getHost(), request.getPort());
    if (request.getScheme().equals("https")) {
      managedChannelBuilder.useTransportSecurity();
    } else {
      managedChannelBuilder.usePlaintext();
    }
    Metadata metadata = new Metadata();
    request.getHeaders().all().forEach(header ->
          metadata.put(Metadata.Key.of(header.key(), Metadata.ASCII_STRING_MARSHALLER), header.firstValue())
    );
    ClientInterceptor clientInterceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
    Channel channel = managedChannelBuilder.intercept(clientInterceptor).build();

    List<HttpHeader> headers = new ArrayList<>();
    headers.add(new HttpHeader("Content-Type", "application/json"));
    Response.Builder grpcRespBuilder = response();
    String statusName = Status.Code.OK.name();
    String statusReason = null;
    try {
      DynamicMessage responseMsg =
          ClientCalls.blockingUnaryCall(
              channel,
              GrpcUtils.buildMessageDescriptorInstance(
                  context.getServiceDescriptor(), context.getMethodDescriptor()),
              CallOptions.DEFAULT,
              context.getDm());
      JsonMessageConverter converter = context.getJsonMessageConverter();
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

    return grpcRespBuilder.headers(new HttpHeaders(headers.toArray(HttpHeader[]::new))).build();
  }
}
