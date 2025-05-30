/*
 * Copyright (C) 2024 Thomas Akehurst
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

import com.github.tomakehurst.wiremock.common.Pair;
import io.grpc.Status;
import java.util.Map;

public class GrpcStatusUtils {

  private GrpcStatusUtils() {}

  // https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
  public static final Map<Integer, Pair<Status, String>> errorHttpToGrpcStatusMappings =
      Map.of(
          400,
          new Pair<>(Status.INTERNAL, "Bad Request"),
          401,
          new Pair<>(Status.UNAUTHENTICATED, "You are not authorized to access this resource"),
          403,
          new Pair<>(Status.PERMISSION_DENIED, "You are not authorized to access this resource"),
          404,
          new Pair<>(Status.UNIMPLEMENTED, "No matching stub mapping found for gRPC request"),
          429,
          new Pair<>(Status.UNAVAILABLE, "Too many requests"),
          502,
          new Pair<>(Status.UNAVAILABLE, "Bad Gateway"),
          503,
          new Pair<>(Status.UNAVAILABLE, "Service Unavailable"),
          504,
          new Pair<>(Status.UNAVAILABLE, "Gateway Timeout"));
}
