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
package org.wiremock.grpc.dsl;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import org.wiremock.annotations.Beta;

@Beta(justification = "Incubating extension: https://github.com/wiremock/wiremock/issues/2383")
public class WireMockGrpcService {

  private final WireMock wireMock;
  private final String serviceName;

  public WireMockGrpcService(WireMock wireMock, String serviceName) {
    this.wireMock = wireMock;
    this.serviceName = serviceName;
  }

  public StubMapping stubFor(GrpcStubMappingBuilder builder) {
    final StubMapping stubMapping = builder.build(serviceName);
    wireMock.register(stubMapping);
    return stubMapping;
  }

  public GrpcVerification verify(String method) {
    return new GrpcVerification(wireMock, moreThanOrExactly(1), serviceName, method);
  }

  public GrpcVerification verify(int count, String method) {
    return new GrpcVerification(wireMock, exactly(count), serviceName, method);
  }

  public GrpcVerification verify(CountMatchingStrategy countMatch, String method) {
    return new GrpcVerification(wireMock, countMatch, serviceName, method);
  }

  /** Removes all transient stubs for the current gRPC service */
  public void removeAllStubs() {
    final String servicePath = "/" + serviceName;

    wireMock.allStubMappings().getMappings().stream()
        .filter(
            mapping -> {
              final RequestPattern requestMatcher = mapping.getRequest();
              final RequestMethod requestMethod = requestMatcher.getMethod();
              final String requestPath = requestMatcher.getUrlPath();

              return (requestMethod != null)
                  && (requestPath != null)
                  && requestMethod.match(RequestMethod.POST).isExactMatch()
                  && requestPath.startsWith(servicePath);
            })
        .forEach(wireMock::removeStubMapping);
  }
}
