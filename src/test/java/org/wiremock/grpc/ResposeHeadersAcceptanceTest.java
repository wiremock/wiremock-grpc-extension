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
package org.wiremock.grpc;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.example.grpc.GreetingServiceGrpc;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.grpc.client.GreetingsClient;
import org.wiremock.grpc.dsl.WireMockGrpcService;

public class ResposeHeadersAcceptanceTest {

  public static final String X_MY_HEADER = "x-my-Header";
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
  void httpResponseHeadersAreAddedToTheGrpcTrailers() {
    TrailerReadingInterceptor trailerReadingInterceptor = new TrailerReadingInterceptor();
    channel = ClientInterceptors.intercept(managedChannel, trailerReadingInterceptor);
    greetingsClient = new GreetingsClient(channel);
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
            .willReturn(
                okJson("{\n" + "    \"greeting\": \"Howdy!\"\n" + "}")
                    .withHeader(X_MY_HEADER, "first", "second", "third")));

    String greeting = greetingsClient.greet("Whatever");

    assertThat(greeting, is("Howdy!"));

    Metadata capturedTrailers = trailerReadingInterceptor.getCapturedTrailers();
    Metadata.Key<String> key = Metadata.Key.of("x-my-header", Metadata.ASCII_STRING_MARSHALLER);
    assertThat(capturedTrailers.get(key), is("first,second,third"));
  }

  public static class TrailerReadingInterceptor implements ClientInterceptor {
    private Metadata capturedTrailers;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
      return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
          next.newCall(method, callOptions)) {
        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
          super.start(
              new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(
                  responseListener) {

                @Override
                public void onClose(Status status, Metadata trailers) {
                  capturedTrailers = trailers;
                  super.onClose(status, trailers);
                }
              },
              headers);
        }
      };
    }

    public Metadata getCapturedTrailers() {
      return capturedTrailers;
    }
  }
}
