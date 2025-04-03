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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Encoding;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.StubMappingTransformer;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.matching.*;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.nio.charset.StandardCharsets;

public class GrpcStubMappingTransformer extends StubMappingTransformer {
  @Override
  public StubMapping transform(StubMapping stubMapping, FileSource files, Parameters parameters) {
    ResponseDefinition resp = stubMapping.getResponse();
    if (resp.getHeaders() != null
        && resp.getHeaders().getContentTypeHeader() != null
        && resp.getHeaders().getContentTypeHeader().getValues().contains("application/grpc")) {
      // when response is grpc, we need to convert the response body to json
      ResponseDefinition jsonResp = convertBinaryToJson(resp);
      stubMapping.setResponse(jsonResp);

      // when response is grpc, we need to convert the request body to json as well
      RequestPattern req = stubMapping.getRequest();
      RequestPattern jsonReq = convertBinaryToJson(req);
      stubMapping.setRequest(jsonReq);
    }
    return stubMapping;
  }

  private ResponseDefinition convertBinaryToJson(ResponseDefinition resp) {
    String base64Body = resp.getBase64Body();
    if (base64Body != null) {
      byte[] rawBytes = Encoding.decodeBase64(base64Body);
      return ResponseDefinitionBuilder.like(resp)
          .but()
          .withBase64Body(null)
          .withJsonBody(Body.fromJsonBytes(rawBytes).asJson())
          .build();
    } else {
      return resp;
    }
  }

  private RequestPattern convertBinaryToJson(RequestPattern req) {
    RequestPatternBuilder reqBuilder =
        RequestPatternBuilder.newRequestPattern(req.getMethod(), req.getUrlMatcher())
            .withScheme(req.getScheme())
            .withHost(req.getHost());
    if (req.getPort() != null) {
      reqBuilder.withPort(req.getPort());
    }
    if (req.getHeaders() != null) {
      req.getHeaders().forEach(reqBuilder::withHeader);
    }
    if (req.getPathParameters() != null) {
      req.getPathParameters().forEach(reqBuilder::withPathParam);
    }
    if (req.getQueryParameters() != null) {
      req.getQueryParameters().forEach(reqBuilder::withQueryParam);
    }
    if (req.getFormParameters() != null) {
      req.getFormParameters().forEach(reqBuilder::withFormParam);
    }
    if (req.getCookies() != null) {
      req.getCookies().forEach(reqBuilder::withCookie);
    }
    if (req.getBodyPatterns() != null) {
      req.getBodyPatterns()
          .forEach(
              body -> {
                if (body instanceof BinaryEqualToPattern) {
                  BinaryEqualToPattern binaryPattern = (BinaryEqualToPattern) body;
                  byte[] bytes = binaryPattern.getValue();
                  reqBuilder.withRequestBody(
                      new EqualToJsonPattern(
                          new String(bytes, StandardCharsets.UTF_8), true, false));
                } else {
                  reqBuilder.withRequestBody(body);
                }
              });
    }
    if (req.hasInlineCustomMatcher()) {
      reqBuilder.andMatching(req.getMatcher());
    }
    if (req.getMultipartPatterns() != null) {
      req.getMultipartPatterns().forEach(reqBuilder::withRequestBodyPart);
    }
    reqBuilder.withBasicAuth(req.getBasicAuthCredentials());
    reqBuilder.andMatching(req.getCustomMatcher());
    return reqBuilder.build();
  }

  @Override
  public String getName() {
    return "grpc-stub-transformer";
  }
}
