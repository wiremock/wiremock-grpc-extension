/*
 * Copyright (C) 2023 Thomas Akehurst
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

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static org.wiremock.grpc.internal.UrlUtils.grpcUrlPath;

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

public class GrpcVerification {

  private final WireMock wireMock;
  private final CountMatchingStrategy countMatch;

  private final String serviceName;
  private final String method;

  public GrpcVerification(
      WireMock wireMock, CountMatchingStrategy countMatch, String serviceName, String method) {
    this.wireMock = wireMock;
    this.countMatch = countMatch;
    this.serviceName = serviceName;
    this.method = method;
  }

  public void withRequestMessage(StringValuePattern matcher) {
    wireMock.verifyThat(
        countMatch, postRequestedFor(grpcUrlPath(serviceName, method)).withRequestBody(matcher));
  }
}
