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
import static org.wiremock.grpc.internal.Delays.delayIfRequired;

import com.github.tomakehurst.wiremock.common.Pair;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.wiremock.grpc.dsl.WireMockGrpc;

import java.util.Base64;

public class UnaryServerCallHandler extends BaseCallHandler
    implements ServerCalls.UnaryMethod<DynamicMessage, DynamicMessage> {

  public static final Metadata.Key<com.google.rpc.Status> STATUS_DETAILS_KEY =
      Metadata.Key.of("grpc-status-details-bin", ProtoUtils.metadataMarshaller(
          com.google.rpc.Status.getDefaultInstance()));

  public UnaryServerCallHandler(
      StubRequestHandler stubRequestHandler,
      Descriptors.ServiceDescriptor serviceDescriptor,
      Descriptors.MethodDescriptor methodDescriptor,
      JsonMessageConverter jsonMessageConverter) {
    super(stubRequestHandler, serviceDescriptor, methodDescriptor, jsonMessageConverter);
  }

  @Override
  public void invoke(DynamicMessage request, StreamObserver<DynamicMessage> responseObserver) {
    final GrpcFilter.ServerAddress serverAddress = GrpcFilter.ServerAddress.get();

    CONTEXT.set(
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

          delayIfRequired(resp);

          if (!statusHeader.isPresent()
              && GrpcStatusUtils.errorHttpToGrpcStatusMappings.containsKey(resp.getStatus())) {
            final Pair<Status, String> statusMapping =
                GrpcStatusUtils.errorHttpToGrpcStatusMappings.get(resp.getStatus());
            responseObserver.onError(
                statusMapping.a.withDescription(statusMapping.b).asRuntimeException(getTrailers(resp)));
            return;
          }

          if (statusHeader.isPresent()
              && !statusHeader.firstValue().equals(Status.Code.OK.name())) {
            final HttpHeader statusReasonHeader = resp.getHeaders().getHeader(GRPC_STATUS_REASON);
            final String reason =
                statusReasonHeader.isPresent() ? statusReasonHeader.firstValue() : "";

            WireMockGrpc.Status status = WireMockGrpc.Status.valueOf(statusHeader.firstValue());

            responseObserver.onError(
                Status.fromCodeValue(status.getValue())
                    .withDescription(reason)
                    .asRuntimeException(getTrailers(resp)));
            return;
          }

          DynamicMessage.Builder messageBuilder =
              DynamicMessage.newBuilder(methodDescriptor.getOutputType());

          final DynamicMessage response =
              jsonMessageConverter.toMessage(resp.getBodyAsString(), messageBuilder);
          responseObserver.onNext(response);
          responseObserver.onCompleted();
        },
        ServeEvent.of(wireMockRequest));
  }

  public static Metadata getTrailers(Response resp) {
    final HttpHeader statusDetailsHeader = resp.getHeaders().getHeader(STATUS_DETAILS_KEY.name());
    if (statusDetailsHeader.isPresent()) {
      Metadata trailers = new Metadata();
      byte[] headerValue = Base64.getDecoder().decode(statusDetailsHeader.firstValue());
      try {
        trailers.put(STATUS_DETAILS_KEY, com.google.rpc.Status.parseFrom(headerValue));
        return trailers;
      } catch (InvalidProtocolBufferException e) {
        // It would be nice if we could use the notifier to log this.
        System.err.println("Failed to parse trailers from header. " + e.getMessage());
      }
    }
    return null;
  }
}
