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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.wiremock.grpc.dsl.WireMockGrpc.json;
import static org.wiremock.grpc.dsl.WireMockGrpc.method;

import com.example.grpc.AnotherGreetingServiceGrpc;
import com.example.grpc.GreetingServiceGrpc;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.provider.Arguments;
import org.wiremock.grpc.client.AnotherGreetingsClient;
import org.wiremock.grpc.client.GreetingsClient;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import org.wiremock.grpc.internal.GrpcStatusUtils;

public class Jetty12GrpcViaExtensionScanningTest {

  WireMockGrpcService mockGreetingService;
  WireMockGrpcService anotherMockGreetingService;
  ManagedChannel channel;
  ManagedChannel anotherChannel;
  GreetingsClient greetingsClient;
  AnotherGreetingsClient anotherGreetingsClient;
  WireMock wireMock;

  @RegisterExtension
  public static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .dynamicPort()
                  .withRootDirectory("src/test/resources/wiremock")
                  .extensionScanningEnabled(true))
          .build();

  public static Stream<Arguments> statusProvider() {
    return GrpcStatusUtils.errorHttpToGrpcStatusMappings.entrySet().stream()
        .map(
            entry ->
                Arguments.of(
                    entry.getKey(), entry.getValue().a.getCode().name(), entry.getValue().b));
  }

  @BeforeEach
  void init() {
    wireMock = wm.getRuntimeInfo().getWireMock();
    mockGreetingService = new WireMockGrpcService(wireMock, GreetingServiceGrpc.SERVICE_NAME);
    anotherMockGreetingService =
        new WireMockGrpcService(wireMock, AnotherGreetingServiceGrpc.SERVICE_NAME);

    channel = ManagedChannelBuilder.forAddress("localhost", wm.getPort()).usePlaintext().build();
    greetingsClient = new GreetingsClient(channel);

    anotherChannel =
        ManagedChannelBuilder.forAddress("localhost", wm.getPort()).usePlaintext().build();
    anotherGreetingsClient = new AnotherGreetingsClient(anotherChannel);
  }

  @AfterEach
  void tearDown() {
    channel.shutdown();
    anotherChannel.shutdown();
  }

  @Test
  void returnsResponseBuiltFromJson() {
    mockGreetingService.stubFor(
        method("greeting").willReturn(json("{ \"greeting\": \"Hi Tom from JSON\" }")));

    String greeting = greetingsClient.greet("Whatever");

    assertThat(greeting, is("Hi Tom from JSON"));
  }
}
