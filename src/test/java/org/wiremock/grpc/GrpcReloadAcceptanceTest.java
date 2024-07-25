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
package org.wiremock.grpc;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.grpc.BookingRequest;
import com.example.grpc.BookingResponse;
import com.example.grpc.BookingServiceGrpc;
import com.example.grpc.GreetingServiceGrpc;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.common.InputStreamSource;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.store.BlobStore;
import com.github.tomakehurst.wiremock.store.files.FileSourceBlobStore;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.grpc.client.GreetingsClient;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import org.wiremock.grpc.internal.GrpcHttpServerFactory;

public class GrpcReloadAcceptanceTest {

  WireMockGrpcService mockGreetingService;
  ManagedChannel channel;
  GreetingsClient greetingsClient;
  BookingServiceGrpc.BookingServiceBlockingStub bookingServiceStub;
  WireMock wireMock;
  static DelegateBlobStore grpcBlobStore =
      new DelegateBlobStore(new FileSourceBlobStore(new ClasspathFileSource("wiremock/grpc")));
  static GrpcHttpServerFactory grpcHttpServerFactory = new GrpcHttpServerFactory(grpcBlobStore);

  @RegisterExtension
  public static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .dynamicPort()
                  .withRootDirectory("src/test/resources/wiremock")
                  .extensions(services -> List.of(grpcHttpServerFactory)))
          .build();

  @BeforeEach
  void init() {
    wireMock = wm.getRuntimeInfo().getWireMock();
    mockGreetingService = new WireMockGrpcService(wireMock, GreetingServiceGrpc.SERVICE_NAME);

    channel = ManagedChannelBuilder.forAddress("localhost", wm.getPort()).usePlaintext().build();
    greetingsClient = new GreetingsClient(channel);
    bookingServiceStub = BookingServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void tearDown() {
    channel.shutdown();
  }

  @Test
  void descriptorFileCanBeReloadedAtRuntime() {
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.BookingService/booking"))
            .willReturn(
                okJson(
                        "{\n"
                            + "    \"id\": \"{{jsonPath request.body '$.id'}}\"\n,"
                            + "    \"created\": \"{{now format='epoch'}}\"\n,"
                            + "    \"userId\": \"{{randomValue type='UUID'}}\"\n"
                            + "}")
                    .withTransformers("response-template")));
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

    String bookingId = UUID.randomUUID().toString();
    StatusRuntimeException ex1 =
        assertThrows(
            StatusRuntimeException.class,
            () -> bookingServiceStub.booking(BookingRequest.newBuilder().setId(bookingId).build()));
    assertThat(ex1.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));

    grpcBlobStore.setDelegate(
        new FileSourceBlobStore(new ClasspathFileSource("wiremock/bookings")));
    grpcHttpServerFactory.loadFileDescriptors();

    StatusRuntimeException ex2 =
        assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Tom"));
    assertThat(ex2.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));

    Long before = System.currentTimeMillis();
    BookingResponse booking =
        bookingServiceStub.booking(BookingRequest.newBuilder().setId(bookingId).build());
    Long after = System.currentTimeMillis();

    assertThat(booking.getId(), is(bookingId));
    assertThat(
        Long.valueOf(booking.getCreated()).doubleValue(),
        closeTo(before.doubleValue(), Long.valueOf(after - before).doubleValue()));
    assertThat(
        booking.getUserId(),
        matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
  }

  private static class DelegateBlobStore implements BlobStore {

    private BlobStore delegate;

    private DelegateBlobStore(BlobStore delegate) {
      this.delegate = delegate;
    }

    public void setDelegate(BlobStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<InputStream> getStream(String key) {
      return Optional.empty();
    }

    @Override
    public InputStreamSource getStreamSource(String key) {
      return null;
    }

    @Override
    public Stream<String> getAllKeys() {
      return delegate.getAllKeys();
    }

    @Override
    public Optional<byte[]> get(String key) {
      return delegate.get(key);
    }

    @Override
    public void put(String key, byte[] content) {}

    @Override
    public void remove(String key) {}

    @Override
    public void clear() {}
  }
}
