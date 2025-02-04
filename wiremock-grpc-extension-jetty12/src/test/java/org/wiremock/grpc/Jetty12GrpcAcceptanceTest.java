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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wiremock.grpc.dsl.WireMockGrpc.Status;
import static org.wiremock.grpc.dsl.WireMockGrpc.equalToMessage;
import static org.wiremock.grpc.dsl.WireMockGrpc.json;
import static org.wiremock.grpc.dsl.WireMockGrpc.jsonTemplate;
import static org.wiremock.grpc.dsl.WireMockGrpc.message;
import static org.wiremock.grpc.dsl.WireMockGrpc.messageAsAny;
import static org.wiremock.grpc.dsl.WireMockGrpc.messages;
import static org.wiremock.grpc.dsl.WireMockGrpc.method;

import com.example.grpc.AnotherGreetingServiceGrpc;
import com.example.grpc.GreetingServiceGrpc;
import com.example.grpc.request.HelloRequest;
import com.example.grpc.response.HelloResponse;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.common.base.Stopwatch;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.wiremock.grpc.client.AnotherGreetingsClient;
import org.wiremock.grpc.client.GreetingsClient;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import org.wiremock.grpc.internal.GrpcStatusUtils;
import org.wiremock.grpc.internal.Jetty12GrpcHttpServerFactory;

public class Jetty12GrpcAcceptanceTest {

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
                  .extensions(new Jetty12GrpcExtensionFactory()))
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
  void shouldReturnGreetingBuiltViaTemplatedJsonWithRawStubbing() {
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
            .willReturn(
                okJson(
                        "{\n"
                            + "    \"greeting\": \"Hello {{jsonPath request.body '$.name'}}\"\n"
                            + "}")
                    .withTransformers("response-template")));

    String greeting = greetingsClient.greet("Tom");

    assertThat(greeting, is("Hello Tom"));
  }

  @Test
  void shouldReturnGreetingBuiltViaTemplatedJson() {
    mockGreetingService.stubFor(
        method("greeting")
            .willReturn(
                jsonTemplate("{ \"greeting\": \"Hello {{jsonPath request.body '$.name'}}\" }")));

    String greeting = greetingsClient.greet("Tom");

    assertThat(greeting, is("Hello Tom"));
  }

  @Test
  void returnsResponseBuiltFromJson() {
    mockGreetingService.stubFor(
        method("greeting").willReturn(json("{ \"greeting\": \"Hi Tom from JSON\" }")));

    String greeting = greetingsClient.greet("Whatever");

    assertThat(greeting, is("Hi Tom from JSON"));
  }

  @Test
  void returnsResponseBuiltFromMessageObject() {
    mockGreetingService.stubFor(
        method("greeting")
            .willReturn(message(HelloResponse.newBuilder().setGreeting("Hi Tom from object"))));

    String greeting = greetingsClient.greet("Whatever");

    assertThat(greeting, is("Hi Tom from object"));
  }

  @Test
  void matchesRequestViaCoreJsonMatcher() {
    mockGreetingService.stubFor(
        method("greeting")
            .withRequestMessage(equalToJson("{ \"name\":  \"Tom\" }"))
            .willReturn(message(HelloResponse.newBuilder().setGreeting("OK"))));

    assertThat(greetingsClient.greet("Tom"), is("OK"));

    assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Wrong"));
  }

  @Test
  void matchesRequestViaExactMessageEquality() {
    mockGreetingService.stubFor(
        method("greeting")
            .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Tom")))
            .willReturn(message(HelloResponse.newBuilder().setGreeting("OK"))));

    assertThat(greetingsClient.greet("Tom"), is("OK"));

    StatusRuntimeException exception =
        assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Wrong"));
    assertThat(
        exception.getMessage(),
        is("UNIMPLEMENTED: No matching stub mapping found for gRPC request"));
  }

  @ParameterizedTest
  @MethodSource("statusProvider")
  void shouldReturnTheCorrectGrpcErrorStatusForCorrespondingHttpStatus(
      Integer httpStatus, String grpcStatus, String message) {
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
            .willReturn(aResponse().withStatus(httpStatus)));

    StatusRuntimeException exception =
        assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Tom"));
    assertThat(exception.getMessage(), is(grpcStatus + ": " + message));
  }

  @Test
  void returnsResponseWithStatus() {
    mockGreetingService.stubFor(
        method("greeting").willReturn(Status.FAILED_PRECONDITION, "Failed some blah prerequisite"));

    StatusRuntimeException exception =
        assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Whatever"));
    assertThat(exception.getMessage(), is("FAILED_PRECONDITION: Failed some blah prerequisite"));
  }

  @Test
  void returnsUnaryResponseToFirstMatchingMessagesInStreamingRequest() {
    mockGreetingService.stubFor(
        method("manyGreetingsOneReply")
            .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Rob").build()))
            .willReturn(message(HelloResponse.newBuilder().setGreeting("Hi Rob"))));

    assertThat(greetingsClient.manyGreetingsOneReply("Tom", "Uri", "Rob", "Mark"), is("Hi Rob"));
  }

  @Test
  void throwsNotFoundWhenNoStreamingClientMessageMatches() {
    mockGreetingService.stubFor(
        method("manyGreetingsOneReply")
            .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Jeff").build()))
            .willReturn(message(HelloResponse.newBuilder().setGreeting("Hi Rob"))));

    Exception exception =
        assertThrows(
            Exception.class,
            () -> greetingsClient.manyGreetingsOneReply("Tom", "Uri", "Rob", "Mark"));
    assertThat(exception.getCause(), instanceOf(StatusRuntimeException.class));
    assertThat(
        exception.getCause().getMessage(),
        is("UNIMPLEMENTED: No matching stub mapping found for gRPC request"));
  }

  @Test
  void throwsReturnedErrorFromStreamingClientCall() {
    mockGreetingService.stubFor(
        method("manyGreetingsOneReply")
            .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Jerf").build()))
            .willReturn(Status.INVALID_ARGUMENT, "Jerf is not a valid name"));

    Exception exception =
        assertThrows(
            Exception.class, () -> greetingsClient.manyGreetingsOneReply("Tom", "Jerf", "Rob"));
    assertThat(exception.getCause(), instanceOf(StatusRuntimeException.class));
    assertThat(exception.getCause().getMessage(), is("INVALID_ARGUMENT: Jerf is not a valid name"));
  }

  @ParameterizedTest
  @MethodSource("statusProvider")
  void throwsReturnedErrorFromStreamingClientCallWhenServerOnlyReturnsAHttpStatus(
      Integer httpStatus, String grpcStatus, String message) {
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.GreetingService/manyGreetingsOneReply"))
            .willReturn(aResponse().withStatus(httpStatus)));

    Exception exception =
        assertThrows(
            Exception.class, () -> greetingsClient.manyGreetingsOneReply("Tom", "Jerf", "Rob"));
    assertThat(exception.getCause(), instanceOf(StatusRuntimeException.class));
    assertThat(exception.getCause().getMessage(), is(grpcStatus + ": " + message));
  }

  @Test
  void returnsStreamedResponseToUnaryRequestWithSingleItem() {
    mockGreetingService.stubFor(
        method("oneGreetingManyReplies")
            .willReturn(message(HelloResponse.newBuilder().setGreeting("Hi Tom"))));

    assertThat(greetingsClient.oneGreetingManyReplies("Tom"), hasItem("Hi Tom"));
  }

  @Test
  void returnsStreamedResponseToUnaryRequest() {
    mockGreetingService.stubFor(
        method("oneGreetingManyReplies")
            .willReturn(
                messages(
                    List.of(
                        HelloResponse.newBuilder().setGreeting("Hi Tom"),
                        HelloResponse.newBuilder().setGreeting("Hi Tom again")))));

    assertThat(greetingsClient.oneGreetingManyReplies("Tom"), hasItems("Hi Tom", "Hi Tom again"));
  }

  @Test
  void returnsResponseWithImportedType() {
    mockGreetingService.stubFor(
        method("oneGreetingEmptyReply").willReturn(message(Empty.newBuilder())));

    assertThat(greetingsClient.oneGreetingEmptyReply("Tom"), is(true));
  }

  @Test
  void verifiesViaJson() {
    mockGreetingService.stubFor(
        method("greeting").willReturn(message(HelloResponse.newBuilder().setGreeting("Hi"))));

    greetingsClient.greet("Peter");
    greetingsClient.greet("Peter");

    mockGreetingService
        .verify(moreThanOrExactly(2), "greeting")
        .withRequestMessage(equalToJson("{ \"name\":  \"Peter\" }"));

    mockGreetingService.verify(0, "oneGreetingEmptyReply");

    mockGreetingService
        .verify(0, "greeting")
        .withRequestMessage(equalToJson("{ \"name\":  \"Chris\" }"));
  }

  @Test
  void verifiesViaMessage() {
    mockGreetingService.stubFor(
        method("greeting").willReturn(message(HelloResponse.newBuilder().setGreeting("Hi"))));

    greetingsClient.greet("Peter");

    mockGreetingService
        .verify("greeting")
        .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Peter")));

    mockGreetingService.verify(0, "oneGreetingEmptyReply");
  }

  @Test
  void networkFault() {
    mockGreetingService.stubFor(method("greeting").willReturn(Fault.CONNECTION_RESET_BY_PEER));

    Exception exception =
        assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Alan"));
    assertThat(exception.getMessage(), startsWith("CANCELLED"));
  }

  @Test
  void fixedDelay() {
    mockGreetingService.stubFor(
        method("greeting")
            .willReturn(json("{ \"greeting\": \"Delayed hello\" }"))
            .withFixedDelay(1000));

    Stopwatch stopwatch = Stopwatch.createStarted();
    String greeting = greetingsClient.greet("Tom");
    stopwatch.stop();

    assertThat(greeting, is("Delayed hello"));
    assertThat(stopwatch.elapsed(), greaterThanOrEqualTo(Duration.ofMillis(990L)));
  }

  @Test
  void randomDelay() {
    mockGreetingService.stubFor(
        method("greeting")
            .willReturn(json("{ \"greeting\": \"Delayed hello\" }"))
            .withUniformRandomDelay(500, 1000));

    Stopwatch stopwatch = Stopwatch.createStarted();
    String greeting = greetingsClient.greet("Tom");
    stopwatch.stop();

    assertThat(greeting, is("Delayed hello"));
    assertThat(stopwatch.elapsed(), greaterThanOrEqualTo(Duration.ofMillis(500L)));
  }

  @Test
  void resetStubs() {
    // Starting point assertion
    // There should be a single mapping (the hello-world one)
    verifyDefaultMappings();

    anotherMockGreetingService.stubFor(
        method("anotherGreeting")
            .willReturn(message(HelloResponse.newBuilder().setGreeting("Hello"))));

    mockGreetingService.stubFor(
        method("greeting").willReturn(message(HelloResponse.newBuilder().setGreeting("Hi"))));

    mockGreetingService.stubFor(
        method("oneGreetingEmptyReply").willReturn(message(Empty.newBuilder())));

    assertThat(wireMock.allStubMappings().getMappings(), iterableWithSize(4));

    mockGreetingService.removeAllStubs();
    assertThat(wireMock.allStubMappings().getMappings(), iterableWithSize(2));

    anotherMockGreetingService.removeAllStubs();

    verifyDefaultMappings();
  }

  private void verifyDefaultMappings() {
    var mappings = wireMock.allStubMappings().getMappings();
    assertThat(mappings, iterableWithSize(1));

    var mapping = mappings.get(0);
    assertNotNull(mapping);
    assertThat(mapping.getName(), Matchers.equalTo("Hello"));

    var request = mapping.getRequest();
    assertThat(request.getMethod().value(), Matchers.equalTo("GET"));
    assertThat(request.getUrlPath(), Matchers.equalTo("/hello"));
  }

  @Test
  void resetAll() {
    // Create a single stub for 2 different services
    anotherMockGreetingService.stubFor(
        method("anotherGreeting")
            .willReturn(message(HelloResponse.newBuilder().setGreeting("Hello"))));

    mockGreetingService.stubFor(
        method("greeting").willReturn(message(HelloResponse.newBuilder().setGreeting("Hi"))));

    // Perform some actions on each
    assertThat(greetingsClient.greet("Tom"), is("Hi"));
    assertThat(greetingsClient.greet("Tom"), is("Hi"));
    assertThat(anotherGreetingsClient.greet("Tom"), is("Hello"));
    assertThat(anotherGreetingsClient.greet("Tom"), is("Hello"));

    // Verify the interactions with each
    mockGreetingService
        .verify(2, "greeting")
        .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Tom")));

    anotherMockGreetingService
        .verify(2, "anotherGreeting")
        .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Tom")));

    // Remove all from one of the services
    mockGreetingService.resetAll();

    // Create a new stub
    mockGreetingService.stubFor(
        method("greeting").willReturn(message(HelloResponse.newBuilder().setGreeting("Hello"))));

    // Perform some actions on each
    assertThat(greetingsClient.greet("Tom"), is("Hello"));
    assertThat(greetingsClient.greet("Tom"), is("Hello"));
    assertThat(greetingsClient.greet("Tom"), is("Hello"));
    assertThat(greetingsClient.greet("Tom"), is("Hello"));
    assertThat(anotherGreetingsClient.greet("Tom"), is("Hello"));
    assertThat(anotherGreetingsClient.greet("Tom"), is("Hello"));
    assertThat(anotherGreetingsClient.greet("Tom"), is("Hello"));
    assertThat(anotherGreetingsClient.greet("Tom"), is("Hello"));

    // Verify the interactions with each
    mockGreetingService
        .verify(4, "greeting")
        .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Tom")));

    anotherMockGreetingService
        .verify(6, "anotherGreeting")
        .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Tom")));
  }

  @Test
  void unaryMethodWithAnyRequest() {
    mockGreetingService.stubFor(
        method("greetingAnyRequest")
            .willReturn(message(HelloResponse.newBuilder().setGreeting("Hiya").build())));

    String greeting = greetingsClient.greetAnyRequest();

    assertThat(greeting, is("Hiya"));
  }

  @Test
  void unaryMethodWithAnyResponse() {
    mockGreetingService.stubFor(
        method("greetingAnyResponse")
            .willReturn(messageAsAny(HelloResponse.newBuilder().setGreeting("Hiya").build())));

    String typeUrl = greetingsClient.greetAnyResponse();

    assertThat(typeUrl, is("type.googleapis.com/com.example.grpc.response.HelloResponse"));
  }

  @Test
  void unaryMethodWithAnyResponseFromJson() {
    mockGreetingService.stubFor(
        method("greetingAnyResponse")
            .willReturn(
                json(
                    "{ \"@type\": \"type.googleapis.com/com.example.grpc.response.HelloResponse\", \"greeting\": \"Hiya\" }")));

    String typeUrl = greetingsClient.greetAnyResponse();

    assertThat(typeUrl, is("type.googleapis.com/com.example.grpc.response.HelloResponse"));
  }

  @Test
  void reflection() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    List<ServerReflectionResponse> serverReflectionResponses = new ArrayList<>();
    StreamObserver<ServerReflectionRequest> stream =
        ServerReflectionGrpc.newStub(channel)
            .serverReflectionInfo(
                new StreamObserver<>() {
                  @Override
                  public void onNext(ServerReflectionResponse value) {
                    serverReflectionResponses.add(value);
                  }

                  @Override
                  public void onError(Throwable t) {
                    t.printStackTrace(System.err);
                  }

                  @Override
                  public void onCompleted() {
                    latch.countDown();
                  }
                });
    stream.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
    stream.onCompleted();
    assertTrue(latch.await(5, SECONDS));

    System.out.println(serverReflectionResponses);

    List<ServiceResponse> serviceList =
        serverReflectionResponses.get(0).getListServicesResponse().getServiceList();
    assertThat(serviceList.size(), is(4));
  }
}
