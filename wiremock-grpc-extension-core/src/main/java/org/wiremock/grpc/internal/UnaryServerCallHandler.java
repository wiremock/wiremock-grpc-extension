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
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.Status;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.util.function.Supplier;
import org.wiremock.grpc.dsl.WireMockGrpc;

public class UnaryServerCallHandler extends BaseCallHandler
    implements ServerCalls.UnaryMethod<DynamicMessage, DynamicMessage> {

  public UnaryServerCallHandler(
      StubRequestHandler stubRequestHandler,
      Descriptors.ServiceDescriptor serviceDescriptor,
      Descriptors.MethodDescriptor methodDescriptor,
      JsonMessageConverter jsonMessageConverter,
      Supplier<ServerAddress> serverAddressSupplier) {
    super(
        stubRequestHandler,
        serviceDescriptor,
        methodDescriptor,
        jsonMessageConverter,
        serverAddressSupplier);
  }

  @Override
  public void invoke(DynamicMessage request, StreamObserver<DynamicMessage> responseObserver) {
    final ServerAddress serverAddress = serverAddressSupplier.get();

    CONTEXT.set(
        new GrpcContext(serviceDescriptor, methodDescriptor, jsonMessageConverter, request));

    final GrpcRequest wireMockRequest =
        new GrpcRequest(
            serverAddress.scheme(),
            serverAddress.hostname(),
            serverAddress.port(),
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
                statusMapping.a.withDescription(statusMapping.b).asRuntimeException());
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
                    .asRuntimeException());
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
}
