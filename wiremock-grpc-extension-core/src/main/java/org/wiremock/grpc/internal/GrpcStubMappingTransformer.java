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
package org.wiremock.grpc.internal;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.StubMappingTransformer;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.matching.*;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GrpcStubMappingTransformer extends StubMappingTransformer {
  @Override
  public StubMapping transform(StubMapping stubMapping, FileSource files, Parameters parameters) {
    ResponseDefinition resp = stubMapping.getResponse();
    if (resp.getHeaders() != null
        && resp.getHeaders().getHeader(GrpcUtils.GRPC_STATUS_NAME) != null) {
      // when response is grpc, we need to convert the request body to json as well, the reason that
      // we cannot use request content type is because it is not set in the request pattern
      RequestPattern req = stubMapping.getRequest();
      RequestPattern jsonReq = convertBinaryToJson(req);
      stubMapping.setRequest(jsonReq);
    }
    return stubMapping;
  }

  private RequestPattern convertBinaryToJson(RequestPattern req) {
    RequestPatternBuilder reqBuilder = RequestPatternBuilder.like(req).but();
    List<EqualToJsonPattern> convertedPatterns =
        req.getBodyPatterns().stream()
            .filter(body -> body instanceof BinaryEqualToPattern)
            .map(
                binaryBody -> {
                  byte[] bytes = ((BinaryEqualToPattern) binaryBody).getValue();
                  return new EqualToJsonPattern(
                      new String(bytes, StandardCharsets.UTF_8), true, false);
                })
            .toList();

    reqBuilder.clearBodyPatterns();
    // Add the converted patterns to the request builder, separate this from the stream to avoid
    // concurrent modification exception
    convertedPatterns.forEach(reqBuilder::withRequestBody);
    return reqBuilder.build();
  }

  @Override
  public String getName() {
    return "grpc-stub-transformer";
  }
}
