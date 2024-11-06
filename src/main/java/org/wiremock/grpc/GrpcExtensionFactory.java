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
package org.wiremock.grpc;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import com.github.tomakehurst.wiremock.http.HttpServerFactoryLoader;
import java.util.List;
import org.wiremock.grpc.internal.GrpcHttpServerFactory;

public class GrpcExtensionFactory implements ExtensionFactory {

  @Override
  public List<Extension> create(WireMockServices services) {
    return List.of(new GrpcHttpServerFactory(services.getStores().getBlobStore("grpc")));
  }

  @Override
  public boolean isLoadable() {
    return HttpServerFactoryLoader.isJetty11();
  }
}
