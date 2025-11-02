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

import static org.wiremock.grpc.dsl.GrpcResponseDefinitionBuilder.GRPC_STATUS_NAME;
import static org.wiremock.grpc.dsl.GrpcResponseDefinitionBuilder.GRPC_STATUS_REASON;
import static org.wiremock.grpc.internal.UnaryServerCallHandler.getTrailers;

import com.github.tomakehurst.wiremock.common.Pair;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.http.HttpStatus;
import org.wiremock.grpc.dsl.WireMockGrpc;

public class ClientStreamingServerCallHandler extends BaseCallHandler
    implements ServerCalls.ClientStreamingMethod<DynamicMessage, DynamicMessage> {

  public ClientStreamingServerCallHandler(
      StubRequestHandler stubRequestHandler,
      Descriptors.ServiceDescriptor serviceDescriptor,
      Descriptors.MethodDescriptor methodDescriptor,
      JsonMessageConverter jsonMessageConverter) {
    super(stubRequestHandler, serviceDescriptor, methodDescriptor, jsonMessageConverter);
  }

  @Override
  public StreamObserver<DynamicMessage> invoke(StreamObserver<DynamicMessage> responseObserver) {
    final GrpcFilter.ServerAddress serverAddress = GrpcFilter.ServerAddress.get();

    final AtomicReference<DynamicMessage> firstResponse = new AtomicReference<>();
    final AtomicReference<WireMockGrpc.Status> responseStatus = new AtomicReference<>();
    final AtomicReference<String> statusReason = new AtomicReference<>();
    final AtomicReference<Metadata> trailers = new AtomicReference<>();

    return new StreamObserver<>() {
      @Override
      public void onNext(DynamicMessage request) {
        if (firstResponse.get() != null) {
          return;
        }

        BaseCallHandler.CONTEXT.set(
            new GrpcContext(serviceDescriptor, methodDescriptor, jsonMessageConverter, request));

        final GrpcRequest wireMockRequest =
            new GrpcRequest(
                serverAddress.scheme,
                serverAddress.hostname,
                serverAddress.port,
                serviceDescriptor.getFullName(),
                methodDescriptor.getName(),
                jsonMessageConverter.toJson(request));

        stubRequestHandler.handle(
            wireMockRequest,
            (req, resp, attributes) -> {
              final HttpHeader statusHeader = resp.getHeaders().getHeader(GRPC_STATUS_NAME);

              // 404 needs to be handled as a special case here because when using many requests,
              // one reply not all the requests will match.  We handle the 404 as a special case
              // in the onCompleted method
              if (!statusHeader.isPresent() && resp.getStatus() == HttpStatus.NOT_FOUND_404) {
                return;
              }

              if (!statusHeader.isPresent()
                  && GrpcStatusUtils.errorHttpToGrpcStatusMappings.containsKey(resp.getStatus())) {
                final Pair<Status, String> statusMapping =
                    GrpcStatusUtils.errorHttpToGrpcStatusMappings.get(resp.getStatus());
                final Status grpcStatus = statusMapping.a;
                final WireMockGrpc.Status status =
                    WireMockGrpc.Status.valueOf(grpcStatus.getCode().name());

                responseStatus.set(status);
                statusReason.set(statusMapping.b);
                trailers.set(getTrailers(resp));

                return;
              }

              if (statusHeader.isPresent()
                  && !statusHeader.firstValue().equals(Status.Code.OK.name())) {
                final HttpHeader statusReasonHeader =
                    resp.getHeaders().getHeader(GRPC_STATUS_REASON);
                final String reason =
                    statusReasonHeader.isPresent() ? statusReasonHeader.firstValue() : "";

                WireMockGrpc.Status status = WireMockGrpc.Status.valueOf(statusHeader.firstValue());

                responseStatus.set(status);
                statusReason.set(reason);
                trailers.set(getTrailers(resp));

                return;
              }

              DynamicMessage.Builder messageBuilder =
                  DynamicMessage.newBuilder(methodDescriptor.getOutputType());

              final DynamicMessage response =
                  jsonMessageConverter.toMessage(resp.getBodyAsString(), messageBuilder);

              responseStatus.set(WireMockGrpc.Status.OK);
              firstResponse.set(response);
            },
            ServeEvent.of(wireMockRequest));
      }

      @Override
      public void onError(Throwable t) {}

      @Override
      public void onCompleted() {
        if (responseStatus.get() != null && responseStatus.get() == WireMockGrpc.Status.OK) {
          responseObserver.onNext(firstResponse.get());
          responseObserver.onCompleted();
        } else if (responseStatus.get() != null && responseStatus.get() != WireMockGrpc.Status.OK) {
          responseObserver.onError(
              Status.fromCodeValue(responseStatus.get().getValue())
                  .withDescription(statusReason.get())
                  .asRuntimeException(trailers.get()));
        } else {
          final Pair<Status, String> notFoundStatusMapping =
              GrpcStatusUtils.errorHttpToGrpcStatusMappings.get(HttpStatus.NOT_FOUND_404);
          final Status grpcStatus = notFoundStatusMapping.a;

          responseObserver.onError(
              grpcStatus.withDescription(notFoundStatusMapping.b).asRuntimeException(trailers.get()));
        }
      }
    };
  }
}
