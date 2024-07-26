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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.grpc.BookingRequest;
import com.example.grpc.BookingResponse;
import com.example.grpc.BookingServiceGrpc;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.store.BlobStore;
import com.github.tomakehurst.wiremock.store.files.FileSourceBlobStore;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.wiremock.grpc.client.GreetingsClient;
import org.wiremock.grpc.internal.GrpcHttpServerFactory;

public class GrpcReloadAcceptanceTest {

  ManagedChannel channel;
  GreetingsClient greetingsClient;
  BookingServiceGrpc.BookingServiceBlockingStub bookingServiceStub;
  static @TempDir File tempDir;
  BlobStore blobStore = new FileSourceBlobStore(new SingleRootFileSource(tempDir));
  GrpcHttpServerFactory grpcHttpServerFactory = new GrpcHttpServerFactory(blobStore);

  @RegisterExtension
  public WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig().dynamicPort().extensions(services -> List.of(grpcHttpServerFactory)))
          .build();

  @BeforeEach
  void init() {
    blobStore.clear();
    channel = ManagedChannelBuilder.forAddress("localhost", wm.getPort()).usePlaintext().build();
    greetingsClient = new GreetingsClient(channel);
    bookingServiceStub = BookingServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void tearDown() {
    channel.shutdown();
  }

  @Test
  void descriptorFileCanBeReloadedAtRuntime() throws IOException {
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.BookingService/booking"))
            .willReturn(
                okJson("{\n" + "    \"id\": \"{{jsonPath request.body '$.id'}}\"\n" + "}")
                    .withTransformers("response-template")));
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
            .willReturn(
                okJson(
                        "{\n"
                            + "    \"greeting\": \"Hello {{jsonPath request.body '$.name'}}\"\n"
                            + "}")
                    .withTransformers("response-template")));

    File descriptorFile = File.createTempFile("services", ".dsc", tempDir);
    Files.copy(
        Paths.get("src/test/resources/wiremock/grpc/greetings.dsc"),
        descriptorFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING);
    grpcHttpServerFactory.loadFileDescriptors();

    String greeting = greetingsClient.greet("Tom");

    assertThat(greeting, is("Hello Tom"));

    String bookingId = UUID.randomUUID().toString();
    StatusRuntimeException ex1 =
        assertThrows(
            StatusRuntimeException.class,
            () -> bookingServiceStub.booking(BookingRequest.newBuilder().setId(bookingId).build()));
    assertThat(ex1.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));

    Files.copy(
        Paths.get("src/test/resources/wiremock/grpc/bookings.dsc"),
        descriptorFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING);
    grpcHttpServerFactory.loadFileDescriptors();

    StatusRuntimeException ex2 =
        assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Tom"));
    assertThat(ex2.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));

    BookingResponse booking =
        bookingServiceStub.booking(BookingRequest.newBuilder().setId(bookingId).build());

    assertThat(booking.getId(), is(bookingId));
  }
}
