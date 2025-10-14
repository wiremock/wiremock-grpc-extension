/*
 * Copyright (C) 2023-2025 Thomas Akehurst
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

import static org.wiremock.grpc.internal.UrlUtils.grpcUrlPath;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.DelayDistribution;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.LogNormal;
import com.github.tomakehurst.wiremock.http.UniformDistribution;
import com.github.tomakehurst.wiremock.matching.MultiValuePattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.wiremock.annotations.Beta;

@Beta(justification = "Incubating extension: https://github.com/wiremock/wiremock/issues/2383")
public class GrpcStubMappingBuilder {

  private String method;
  private GrpcResponseDefinitionBuilder responseBuilder;

  private List<StringValuePattern> requestMessageJsonPatterns = new ArrayList<>();
  private Map<String, StringValuePattern> stringValuePatternMap = new HashMap<>();
  private Map<String, MultiValuePattern> multiValuePatternMap = new HashMap<>();

  public GrpcStubMappingBuilder(String method) {
    this.method = method;
  }

  public GrpcStubMappingBuilder withRequestMessage(StringValuePattern requestMessageJsonPattern) {
    this.requestMessageJsonPatterns.add(requestMessageJsonPattern);
    return this;
  }

  public GrpcStubMappingBuilder withHeader(String header, StringValuePattern pattern) {
    this.stringValuePatternMap.put(header, pattern);
    return this;
  }

  public GrpcStubMappingBuilder withHeader(String header, MultiValuePattern pattern) {
    this.multiValuePatternMap.put(header, pattern);
    return this;
  }

  public GrpcStubMappingBuilder willReturn(GrpcResponseDefinitionBuilder responseBuilder) {
    this.responseBuilder = responseBuilder;
    return this;
  }

  public GrpcStubMappingBuilder willReturn(WireMockGrpc.Status status, String statusReason) {
    this.responseBuilder = new GrpcResponseDefinitionBuilder(status, statusReason);
    return this;
  }

  public GrpcStubMappingBuilder willReturn(Fault fault) {
    this.responseBuilder = new GrpcResponseDefinitionBuilder(fault);
    return this;
  }

  public GrpcStubMappingBuilder withFixedDelay(long milliseconds) {
    responseBuilder.withFixedDelay(milliseconds);
    return this;
  }

  public GrpcStubMappingBuilder withDelay(DelayDistribution delay) {
    responseBuilder.withRandomDelay(delay);
    return this;
  }

  public GrpcStubMappingBuilder withLogNormalRandomDelay(double medianMilliseconds, double sigma) {
    return withDelay(new LogNormal(medianMilliseconds, sigma));
  }

  public GrpcStubMappingBuilder withUniformRandomDelay(
      int lowerMilliseconds, int upperMilliseconds) {
    return withDelay(new UniformDistribution(lowerMilliseconds, upperMilliseconds));
  }

  public StubMapping build(String serviceName) {
    final MappingBuilder mappingBuilder = WireMock.post(grpcUrlPath(serviceName, method));
    requestMessageJsonPatterns.forEach(mappingBuilder::withRequestBody);
    stringValuePatternMap.forEach((mappingBuilder::withHeader));
    multiValuePatternMap.forEach((mappingBuilder::withHeader));
    return mappingBuilder.willReturn(responseBuilder.build()).build();
  }
}
