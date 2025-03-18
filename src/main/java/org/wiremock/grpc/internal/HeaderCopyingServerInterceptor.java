/*
 * Copyright (C) 2024 Thomas Akehurst
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

import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import io.grpc.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HeaderCopyingServerInterceptor implements ServerInterceptor {

  public static final Context.Key<HttpHeaders> HTTP_HEADERS_CONTEXT_KEY =
      Context.key("HTTP_HEADERS_CONTEXT_KEY");

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    final HttpHeaders httpHeaders = buildHttpHeaders(headers);
    Context newContext = Context.current().withValue(HTTP_HEADERS_CONTEXT_KEY, httpHeaders);
    return Contexts.interceptCall(newContext, call, headers, next);
  }

  private static HttpHeaders buildHttpHeaders(Metadata metadata) {
    final List<HttpHeader> httpHeaderList = metadata.keys().stream().map(key -> {
      if (key.endsWith("-bin")) {
        // Use the binary marshaller for binary headers
        return new HttpHeader(key, Arrays.toString(metadata.get(Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER))));
      } else {
        // Use ASCII marshaller for normal headers
        return new HttpHeader(key, metadata.get(Metadata.Key.of(key, ASCII_STRING_MARSHALLER)));
      }
    }).collect(Collectors.toList());
    return new HttpHeaders(httpHeaderList);
  }
}
