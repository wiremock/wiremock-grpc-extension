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
package org.wiremock.grpc;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.grpc.BookingRequest;
import com.example.grpc.BookingResponse;
import com.example.grpc.BookingServiceGrpc;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wiremock.grpc.client.GreetingsClient;

public class GrpcReloadAcceptanceTest {

  ManagedChannel channel;
  GreetingsClient greetingsClient;
  BookingServiceGrpc.BookingServiceBlockingStub bookingServiceStub;
  @TempDir Path tempDir;
  HttpClient httpClient = HttpClient.newHttpClient();

  public WireMockServer wm;

  @BeforeEach
  void init() throws IOException {
    Files.createDirectory(tempDir.resolve("grpc"));
    wm =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .withRootDirectory(tempDir.toAbsolutePath().toString())
                .extensions(new GrpcExtensionFactory()));
    wm.start();
    channel = ManagedChannelBuilder.forAddress("localhost", wm.port()).usePlaintext().build();
    greetingsClient = new GreetingsClient(channel);
    bookingServiceStub = BookingServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void tearDown() {
    channel.shutdown();
    wm.stop();
  }

  @Test
  void descriptorFileCanBeReloadedAtRuntime() throws Exception {
    stubGrpcMethods();

    writeDescriptorFile("src/test/resources/wiremock/grpc/greetings.dsc");
    reloadDescriptorFile();

    String greeting = greetingsClient.greet("Tom");
    assertThat(greeting, is("Hello Tom"));

    String bookingId = UUID.randomUUID().toString();
    StatusRuntimeException ex1 =
        assertThrows(
            StatusRuntimeException.class,
            () -> bookingServiceStub.booking(BookingRequest.newBuilder().setId(bookingId).build()));
    assertThat(ex1.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));

    writeDescriptorFile("src/test/resources/wiremock/grpc/bookings.dsc");
    reloadDescriptorFile();

    StatusRuntimeException ex2 =
        assertThrows(StatusRuntimeException.class, () -> greetingsClient.greet("Tom"));
    assertThat(ex2.getStatus().getCode(), is(Status.Code.UNIMPLEMENTED));

    BookingResponse booking =
        bookingServiceStub.booking(BookingRequest.newBuilder().setId(bookingId).build());
    assertThat(booking.getId(), is(bookingId));
  }

  private void stubGrpcMethods() {
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

  private void reloadDescriptorFile() throws IOException, InterruptedException {
    HttpRequest loadFileDescriptorsHttpRequest =
        HttpRequest.newBuilder(URI.create(wm.baseUrl()).resolve("/__admin/ext/grpc/reset"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpResponse<String> response1 =
        httpClient.send(loadFileDescriptorsHttpRequest, HttpResponse.BodyHandlers.ofString());
    assertThat(response1.statusCode(), is(200));
    assertThat(response1.body(), emptyString());
  }

  private void writeDescriptorFile(String descriptorFile) throws IOException {
    Files.copy(
        Paths.get(descriptorFile),
        tempDir.resolve("grpc/services.dsc"),
        StandardCopyOption.REPLACE_EXISTING);
  }
}
