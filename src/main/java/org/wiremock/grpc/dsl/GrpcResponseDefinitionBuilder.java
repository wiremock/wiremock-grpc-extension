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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.DelayDistribution;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.FixedDelayDistribution;
import org.wiremock.annotations.Beta;

@Beta(justification = "Incubating extension: https://github.com/wiremock/wiremock/issues/2383")
public class GrpcResponseDefinitionBuilder {

  public static final String GRPC_STATUS_NAME = "grpc-status-name";
  public static final String GRPC_STATUS_REASON = "grpc-status-reason";
  private final WireMockGrpc.Status grpcStatus;
  private final String statusReason;

  private final Fault fault;

  private String json;

  private boolean templatingEnabled;
  private DelayDistribution delayDistribution;

  public GrpcResponseDefinitionBuilder(WireMockGrpc.Status grpcStatus) {
    this(grpcStatus, null);
  }

  public GrpcResponseDefinitionBuilder(WireMockGrpc.Status grpcStatus, String statusReason) {
    this.grpcStatus = grpcStatus;
    this.statusReason = statusReason;
    this.fault = null;
  }

  public GrpcResponseDefinitionBuilder(Fault fault) {
    this.fault = fault;
    this.grpcStatus = null;
    this.statusReason = null;
  }

  public GrpcResponseDefinitionBuilder fromJson(String json) {
    this.json = json;
    return this;
  }

  public GrpcResponseDefinitionBuilder withTemplatingEnabled(boolean enabled) {
    this.templatingEnabled = enabled;
    return this;
  }

  public GrpcResponseDefinitionBuilder withDelay(DelayDistribution delayDistribution) {
    this.delayDistribution = delayDistribution;
    return this;
  }

  public ResponseDefinitionBuilder build() {
    if (fault != null) {
      return ResponseDefinitionBuilder.responseDefinition().withFault(fault);
    }

    final ResponseDefinitionBuilder responseDefinitionBuilder =
        ResponseDefinitionBuilder.responseDefinition()
            .withHeader(GRPC_STATUS_NAME, grpcStatus.name());

    if (statusReason != null) {
      responseDefinitionBuilder.withHeader(GRPC_STATUS_REASON, statusReason);
    }

    if (templatingEnabled) {
      responseDefinitionBuilder.withTransformers("response-template");
    }

    if (delayDistribution != null) {
      responseDefinitionBuilder.withRandomDelay(delayDistribution);
    }

    return responseDefinitionBuilder.withBody(json);
  }
}
