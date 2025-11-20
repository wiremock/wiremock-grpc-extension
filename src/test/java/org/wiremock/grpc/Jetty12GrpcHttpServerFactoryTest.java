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

import static com.github.tomakehurst.wiremock.common.JettySettings.Builder.aJettySettings;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.wiremock.grpc.internal.Jetty12GrpcHttpServerFactory;

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
}
