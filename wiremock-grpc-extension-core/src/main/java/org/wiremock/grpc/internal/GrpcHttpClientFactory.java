/*
 * Copyright (C) 2025-2026 Thomas Akehurst
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
package org.wiremock.grpc.internal;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.client.HttpClient;
import com.github.tomakehurst.wiremock.http.client.HttpClientFactory;
import com.github.tomakehurst.wiremock.http.client.apache5.ApacheHttpClientFactory;
import java.util.List;

public class GrpcHttpClientFactory implements HttpClientFactory {
  private final HttpClientFactory delegateFactory;

  public GrpcHttpClientFactory() {
    this(new ApacheHttpClientFactory());
  }

  public GrpcHttpClientFactory(HttpClientFactory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public String getName() {
    return "grpc-client-factory";
  }

  @Override
  public HttpClient buildHttpClient(
      Options options,
      boolean trustAllCertificates,
      List<String> trustedHosts,
      boolean useSystemProperties) {
    return new GrpcClient(
        delegateFactory.buildHttpClient(
            options, trustAllCertificates, trustedHosts, useSystemProperties));
  }
}
