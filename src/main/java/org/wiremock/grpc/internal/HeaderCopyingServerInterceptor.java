/*
 * Copyright (C) 2024-2025 Thomas Akehurst
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

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Metadata.BINARY_BYTE_MARSHALLER;
import static java.util.stream.Collectors.toList;

import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class HeaderCopyingServerInterceptor implements ServerInterceptor {

  public static final Context.Key<HttpHeaders> HTTP_REQUEST_HEADERS_CONTEXT_KEY = Context.key("HTTP_REQUEST_HEADERS_CONTEXT_KEY");

  public static final Context.Key<AtomicReference<HttpHeaders>> HTTP_RESPONSE_HEADERS_CONTEXT_KEY =
      Context.key("HTTP_RESPONSE_HEADERS_CONTEXT_KEY");

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    final HttpHeaders httpHeaders = toHttpHeaders(headers);
    Context newContext = Context.current().withValue(HTTP_REQUEST_HEADERS_CONTEXT_KEY, httpHeaders)
        .withValue(HTTP_RESPONSE_HEADERS_CONTEXT_KEY, new AtomicReference<>());
    ServerCall<ReqT, RespT> responseHeadersHttpToGrpc = new HttpResponseHeadersToGrpcHeadersForwardingServerCall<>(call);
    return Contexts.interceptCall(newContext, responseHeadersHttpToGrpc, headers, next);
  }

  private static HttpHeaders toHttpHeaders(Metadata metadata) {
    final List<HttpHeader> httpHeaderList =
        metadata.keys().stream()
            .map(
                key -> {
                  if (key.endsWith("-bin")) {
                    // Use the binary marshaller for binary headers
                    return new HttpHeader(
                        key,
                        Arrays.toString(
                            metadata.get(Metadata.Key.of(key, BINARY_BYTE_MARSHALLER))));
                  } else {
                    // Use ASCII marshaller for normal headers
                    return new HttpHeader(
                        key, metadata.get(Metadata.Key.of(key, ASCII_STRING_MARSHALLER)));
                  }
                }).collect(toList());
    return new HttpHeaders(httpHeaderList);
  }

  private static Metadata fromHttpHeaders(HttpHeaders httpHeaders) {
    Metadata metadata = new Metadata();
    httpHeaders.all().forEach(responseHttpHeader -> responseHttpHeader.values()
        .forEach(v -> metadata.put(Metadata.Key.of(responseHttpHeader.key(), ASCII_STRING_MARSHALLER), v)));
    return metadata;
  }

  private static class HttpResponseHeadersToGrpcHeadersForwardingServerCall<ReqT, RespT>
      extends SimpleForwardingServerCall<ReqT, RespT> {

    public HttpResponseHeadersToGrpcHeadersForwardingServerCall(ServerCall<ReqT, RespT> call) {
      super(call);
    }

    @Override
    public void sendHeaders(Metadata headers) {
      HttpHeaders responseHttpHeaders = HTTP_RESPONSE_HEADERS_CONTEXT_KEY.get().get();
      if (responseHttpHeaders != null) {
        headers.merge(fromHttpHeaders(responseHttpHeaders));
      }
      super.sendHeaders(headers);
    }
  }
}
