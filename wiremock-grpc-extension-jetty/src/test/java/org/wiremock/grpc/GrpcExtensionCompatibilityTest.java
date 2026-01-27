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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.http.HttpServerFactory;
import java.lang.reflect.Method;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.wiremock.grpc.jetty.Jetty12GrpcHttpServerFactory;

/**
 * Tests verifying the gRPC extension's compatibility with WireMock core.
 *
 * <p>WireMock 4.0.0-beta.26 introduced a breaking change to the {@link HttpServerFactory}
 * interface - the {@code buildHttpServer} method now requires an additional
 * {@code MessageStubRequestHandler} parameter. This test suite ensures the gRPC extension
 * properly implements the updated interface.
 */
public class GrpcExtensionCompatibilityTest {

  /**
   * Verifies that Jetty12GrpcHttpServerFactory implements the 4-parameter buildHttpServer
   * method required by WireMock 4.0.0-beta.26+.
   *
   * <p>Prior to beta.26, the signature was:
   * <pre>buildHttpServer(Options, AdminRequestHandler, StubRequestHandler)</pre>
   *
   * <p>From beta.26 onwards, the signature is:
   * <pre>buildHttpServer(Options, AdminRequestHandler, StubRequestHandler, MessageStubRequestHandler)</pre>
   */
  @Test
  void implementsCurrentBuildHttpServerSignature() {
    Method[] methods = Jetty12GrpcHttpServerFactory.class.getMethods();

    boolean hasFourParameterMethod = false;
    for (Method method : methods) {
      if (method.getName().equals("buildHttpServer") && method.getParameterCount() == 4) {
        hasFourParameterMethod = true;
        Class<?>[] paramTypes = method.getParameterTypes();
        assertThat(paramTypes[0].getSimpleName(), is("Options"));
        assertThat(paramTypes[1].getSimpleName(), is("AdminRequestHandler"));
        assertThat(paramTypes[2].getSimpleName(), is("StubRequestHandler"));
        assertThat(paramTypes[3].getSimpleName(), is("MessageStubRequestHandler"));
        break;
      }
    }

    assertTrue(hasFourParameterMethod,
        "Jetty12GrpcHttpServerFactory must implement buildHttpServer with 4 parameters");
  }

  /**
   * Verifies that the GrpcExtensionFactory is discoverable via ServiceLoader.
   * This is the standard mechanism WireMock uses to load extensions.
   */
  @Test
  void extensionIsDiscoverableViaServiceLoader() {
    ServiceLoader<com.github.tomakehurst.wiremock.extension.ExtensionFactory> loader =
        ServiceLoader.load(com.github.tomakehurst.wiremock.extension.ExtensionFactory.class);

    boolean foundGrpcExtension = false;
    for (com.github.tomakehurst.wiremock.extension.ExtensionFactory factory : loader) {
      if (factory instanceof GrpcExtensionFactory) {
        foundGrpcExtension = true;
        break;
      }
    }

    assertTrue(foundGrpcExtension,
        "GrpcExtensionFactory should be discoverable via ServiceLoader");
  }
}
