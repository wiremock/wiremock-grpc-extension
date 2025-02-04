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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.Pair;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.Status;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import org.wiremock.grpc.dsl.WireMockGrpc;

public class UnaryServerCallHandler extends BaseCallHandler
    implements ServerCalls.UnaryMethod<DynamicMessage, DynamicMessage> {

  private final ObjectMapper objectMapper = new ObjectMapper();

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

          try (MappingIterator<JsonNode> iterator =
              objectMapper.readerFor(JsonNode.class).readValues(resp.getBody())) {

            while (iterator.hasNext()) {
              JsonNode node = iterator.next();

              final DynamicMessage.Builder messageBuilder =
                  DynamicMessage.newBuilder(methodDescriptor.getOutputType());
              final DynamicMessage response =
                  jsonMessageConverter.toMessage(node.toString(), messageBuilder);

              responseObserver.onNext(response);
            }
          } catch (IOException e) {
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Error parsing response")
                    .withCause(e)
                    .asRuntimeException());
          }
          responseObserver.onCompleted();
        },
        ServeEvent.of(wireMockRequest));
  }
}
