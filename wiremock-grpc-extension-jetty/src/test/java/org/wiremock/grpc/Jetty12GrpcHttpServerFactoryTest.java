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

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.jetty.JettySettings.Builder.aJettySettings;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import com.example.grpc.BookingRequest;
import com.example.grpc.BookingResponse;
import com.example.grpc.BookingServiceGrpc;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.store.files.FileSourceBlobStore;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.wiremock.grpc.client.GreetingsClient;
import org.wiremock.grpc.internal.BlobProtoDescriptorStore;
import org.wiremock.grpc.jetty.Jetty12GrpcHttpServerFactory;

public class Jetty12GrpcHttpServerFactoryTest {

  @Test
  public void obeysJettySettings() {
    {
      var jettySettings = aJettySettings().withResponseHeaderSize(0).build();
      Jetty12GrpcHttpServerFactory grpcHttpServerFactory =
          new Jetty12GrpcHttpServerFactory(jettySettings);
      grpcHttpServerFactory.initProtoDescriptorStore(List::of);
      var exception =
          assertThrowsExactly(
              IllegalArgumentException.class,
              () -> grpcHttpServerFactory.buildHttpServer(new WireMockConfiguration(), null, null));
      assertEquals("Invalid response headers size 0", exception.getMessage());
    }
    {
      var jettySettings = aJettySettings().withResponseHeaderSize(10).build();
      Jetty12GrpcHttpServerFactory grpcHttpServerFactory =
          new Jetty12GrpcHttpServerFactory(jettySettings);
      grpcHttpServerFactory.initProtoDescriptorStore(List::of);
      assertDoesNotThrow(
          () -> grpcHttpServerFactory.buildHttpServer(new WireMockConfiguration(), null, null));
    }
  }

  @Test
  public void helpfulErrorWhenServerIsBuiltBeforeInitialisingDescriptorStore() {
    Jetty12GrpcHttpServerFactory grpcHttpServerFactory = new Jetty12GrpcHttpServerFactory();
    var exception =
        assertThrowsExactly(
            IllegalStateException.class,
            () -> grpcHttpServerFactory.buildHttpServer(new WireMockConfiguration(), null, null));
    assertEquals(
        "Must call initProtoDescriptorStore before using the server factory",
        exception.getMessage());
    grpcHttpServerFactory.initProtoDescriptorStore(List::of);
    assertDoesNotThrow(
        () -> grpcHttpServerFactory.buildHttpServer(new WireMockConfiguration(), null, null));
  }

  @Test
  public void canBuildMultipleServersAndResetDescriptorsForAll()
      throws IOException, InterruptedException {
    Path tempDir = Files.createTempDirectory(null);
    Files.createDirectory(tempDir.resolve("grpc"));
    var factory = new Jetty12GrpcHttpServerFactory();
    factory.initProtoDescriptorStore(
        new BlobProtoDescriptorStore(
            new FileSourceBlobStore(new SingleRootFileSource(tempDir.resolve("grpc").toFile()))));

    WireMockServer wm1 =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .withRootDirectory(tempDir.toAbsolutePath().toString())
                .extensions(factory));
    wm1.start();
    ManagedChannel channel1 =
        ManagedChannelBuilder.forAddress("localhost", wm1.port()).usePlaintext().build();
    GreetingsClient greetingsClient1 = new GreetingsClient(channel1);
    BookingServiceGrpc.BookingServiceBlockingStub bookingServiceStub1 =
        BookingServiceGrpc.newBlockingStub(channel1);

    WireMockServer wm2 =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .withRootDirectory(tempDir.toAbsolutePath().toString())
                .extensions(factory));
    wm2.start();
    ManagedChannel channel2 =
        ManagedChannelBuilder.forAddress("localhost", wm2.port()).usePlaintext().build();
    GreetingsClient greetingsClient2 = new GreetingsClient(channel2);
    BookingServiceGrpc.BookingServiceBlockingStub bookingServiceStub2 =
        BookingServiceGrpc.newBlockingStub(channel2);

    stubGrpcMethods(wm1);
    stubGrpcMethods(wm2);

    writeDescriptorFile(tempDir, "../wiremock-grpc-extension-core/src/test/resources/wiremock/grpc/greetings.dsc");
    reloadDescriptorFile(wm1);
    reloadDescriptorFile(wm2);

    assertThat(greetingsClient1.greet("Tom"), is("Hello Tom"));
    assertThat(greetingsClient2.greet("Ben"), is("Hello Ben"));

    {
      StatusRuntimeException ex =
          assertThrows(
              StatusRuntimeException.class,
              () ->
                  bookingServiceStub1.booking(
                      BookingRequest.newBuilder().setId(UUID.randomUUID().toString()).build()));
      assertThat(ex.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));
    }
    {
      StatusRuntimeException ex =
          assertThrows(
              StatusRuntimeException.class,
              () ->
                  bookingServiceStub2.booking(
                      BookingRequest.newBuilder().setId(UUID.randomUUID().toString()).build()));
      assertThat(ex.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));
    }

    writeDescriptorFile(tempDir, "../wiremock-grpc-extension-core/src/test/resources/wiremock/grpc/bookings.dsc");
    reloadDescriptorFile(wm1);

    {
      StatusRuntimeException ex =
          assertThrows(StatusRuntimeException.class, () -> greetingsClient1.greet("Tom"));
      assertThat(ex.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));
    }
    {
      StatusRuntimeException ex =
          assertThrows(StatusRuntimeException.class, () -> greetingsClient2.greet("Ben"));
      assertThat(ex.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));
    }
    {
      String bookingId = UUID.randomUUID().toString();
      BookingResponse booking =
          bookingServiceStub2.booking(BookingRequest.newBuilder().setId(bookingId).build());
      assertThat(booking.getId(), is(bookingId));
    }

    channel1.shutdown();
    wm1.stop();
    channel2.shutdown();
    wm2.stop();
  }

  private void stubGrpcMethods(WireMockServer wm) {
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.BookingService/booking"))
            .willReturn(
                okJson("{\"id\": \"{{jsonPath request.body '$.id'}}\"}")
                    .withTransformers("response-template")));
    wm.stubFor(
        post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
            .willReturn(
                okJson("{\"greeting\": \"Hello {{jsonPath request.body '$.name'}}\"}")
                    .withTransformers("response-template")));
  }

  private void reloadDescriptorFile(WireMockServer wm) throws IOException, InterruptedException {
    HttpRequest loadFileDescriptorsHttpRequest =
        HttpRequest.newBuilder(URI.create(wm.baseUrl()).resolve("/__admin/ext/grpc/reset"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpResponse<String> response1 =
        HttpClient.newHttpClient()
            .send(loadFileDescriptorsHttpRequest, HttpResponse.BodyHandlers.ofString());
    assertThat(response1.statusCode(), is(200));
    assertThat(response1.body(), emptyString());
  }

  private void writeDescriptorFile(Path dir, String descriptorFile) throws IOException {
    Files.copy(
        Paths.get(descriptorFile),
        dir.resolve("grpc/services.dsc"),
        StandardCopyOption.REPLACE_EXISTING);
  }
}
