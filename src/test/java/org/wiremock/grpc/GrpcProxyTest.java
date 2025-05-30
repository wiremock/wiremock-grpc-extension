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
package org.wiremock.grpc;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.recording.SnapshotRecordResult;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.grpc.client.GreetingsClient;

public class GrpcProxyTest {
  /**
   * This test will generate 2 wiremock servers, in which, wireMock will be used as backend server
   * to serve as a general grpc server wiremockProxy will be the proxy wiremock server
   */
  WireMock wireMock;

  WireMock wireMockProxy;
  ManagedChannel channel;
  GreetingsClient greetingsClient;
  GreetingsClient greetingsStubClient;

  @RegisterExtension
  public static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .dynamicPort()
                  .withRootDirectory("src/test/resources/wiremock")
                  .extensions(new GrpcExtensionFactory()))
          .build();

  @RegisterExtension
  public static WireMockExtension wmProxy =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .dynamicPort()
                  .withRootDirectory("src/test/resources/wiremock_proxy")
                  .extensions(new GrpcExtensionFactory()))
          .build();

  @BeforeEach
  void init() {
    wireMock = wm.getRuntimeInfo().getWireMock();
    wireMockProxy = wmProxy.getRuntimeInfo().getWireMock();
    channel =
        ManagedChannelBuilder.forAddress("localhost", wmProxy.getPort()).usePlaintext().build();
    greetingsClient = new GreetingsClient(channel);
    // Create greetings client for backend service verification
    greetingsStubClient =
        new GreetingsClient(
            ManagedChannelBuilder.forAddress("localhost", wm.getPort()).usePlaintext().build());
  }

  @AfterEach
  void tearDown() {
    channel.shutdown();
  }

  @Test
  public void shouldGreetingFromBackendWhenRecording() {
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
            .willReturn(
                okJson(
                        "{\n"
                            + "    \"greeting\": \"Hello {{jsonPath request.body '$.name'}}\"\n"
                            + "}")
                    .withTransformers("response-template")));
    // Before recording, verify the backend service is replying correctly
    String stubGreet = greetingsStubClient.greet("Tom");
    assertThat("Hello Tom", is(stubGreet));

    wmProxy.startRecording("http://localhost:" + wm.getPort());
    String greet = greetingsClient.greet("Tom");
    SnapshotRecordResult rec_result = wmProxy.stopRecording();
    List<StubMapping> recordedMappings = rec_result.getStubMappings();

    assertThat("Hello Tom", is(greet));
    assertThat(recordedMappings.size(), is(1));
    StubMapping grpcMapping = recordedMappings.get(0);
    RequestPattern grpcReq = grpcMapping.getRequest();
    assertThat(grpcReq.getUrl(), is("/com.example.grpc.GreetingService/greeting"));
    assertThat(grpcReq.getMethod().getName(), is("POST"));
    assertThat(
        (String) grpcReq.getBodyPatterns().get(0).getValue(), is("{\n  \"name\": \"Tom\"\n}"));

    ResponseDefinition grpcResp = grpcMapping.getResponse();
    assertThat(grpcResp.getStatus(), is(200));
    assertThat(grpcResp.getJsonBody().toString(), is("{\"greeting\":\"Hello Tom\"}"));
    assertThat(grpcResp.getHeaders().getHeader("grpc-status-name").firstValue(), is("OK"));
  }
}
