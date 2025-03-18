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
package org.wiremock.grpc;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.example.grpc.GreetingServiceGrpc;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.grpc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.grpc.client.GreetingsClient;
import org.wiremock.grpc.dsl.WireMockGrpcService;

import java.util.Arrays;

public class RequestHeadersAcceptanceTest {

  public static final String X_MY_HEADER = "x-my-Header";
  public static final String X_MY_HEADER_BINARY = "x-my-Header-bin";
  WireMockGrpcService mockGreetingService;
  ManagedChannel managedChannel;
  Channel channel;
  GreetingsClient greetingsClient;
  WireMock wireMock;

  @RegisterExtension
  public static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  //                  .dynamicPort()
                  .port(8282)
                  .withRootDirectory("src/test/resources/wiremock")
                  .extensions(new GrpcExtensionFactory()))
          .build();

  @BeforeEach
  void init() {
    wireMock = wm.getRuntimeInfo().getWireMock();
    mockGreetingService = new WireMockGrpcService(wireMock, GreetingServiceGrpc.SERVICE_NAME);

    managedChannel =
        ManagedChannelBuilder.forAddress("localhost", wm.getPort()).usePlaintext().build();
  }

  @AfterEach
  void tearDown() {
    managedChannel.shutdown();
  }

  @Test
  void arbitraryRequestHeaderCanBeUsedWhenMatchingAndTemplating() {
    channel = ClientInterceptors.intercept(managedChannel, new HeaderAdditionInterceptor());
    greetingsClient = new GreetingsClient(channel);
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
            .withHeader(X_MY_HEADER, equalTo("match me"))
            .willReturn(
                okJson(
                        "{\n"
                            + "    \"greeting\": \"The header value was: {{request.headers.x-my-Header}}\"\n"
                            + "}")
                    .withTransformers("response-template")));

    String greeting = greetingsClient.greet("Whatever");

    assertThat(greeting, is("The header value was: match me"));
  }

  @Test
  void binaryRequestHeaderCanBeUsed() {
    channel = ClientInterceptors.intercept(managedChannel, new BinaryHeaderAdditionInterceptor());
    greetingsClient = new GreetingsClient(channel);
    wm.stubFor(
            post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
                    .withHeader(X_MY_HEADER_BINARY, equalTo(Arrays.toString("binary match me".getBytes())))
                    .willReturn(
                            okJson(
                                    "{\n"
                                            + "    \"greeting\": \"{{request.headers.x-my-Header-bin}}\"\n"
                                            + "}")
                                    .withTransformers("response-template")));

    String greeting = greetingsClient.greet("Whatever");

    assertThat(greeting, is(Arrays.toString("binary match me".getBytes())));
  }

  public static class HeaderAdditionInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> CUSTOM_HEADER_KEY =
        Metadata.Key.of(X_MY_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
      return new ForwardingClientCall.SimpleForwardingClientCall<>(
          next.newCall(method, callOptions)) {

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
          headers.put(CUSTOM_HEADER_KEY, "match me");
          super.start(responseListener, headers);
        }
      };
    }
  }

  public static class BinaryHeaderAdditionInterceptor implements ClientInterceptor {

    static final Metadata.Key<byte[]> CUSTOM_HEADER_KEY =
            Metadata.Key.of(X_MY_HEADER_BINARY, Metadata.BINARY_BYTE_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
      return new ForwardingClientCall.SimpleForwardingClientCall<>(
              next.newCall(method, callOptions)) {

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
          headers.put(CUSTOM_HEADER_KEY, "binary match me".getBytes());
          super.start(responseListener, headers);
        }
      };
    }
  }
}
