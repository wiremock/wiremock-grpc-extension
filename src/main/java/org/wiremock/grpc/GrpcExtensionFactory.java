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
package org.wiremock.grpc;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;

import java.net.URL;
import java.util.List;
import org.wiremock.annotations.Beta;
import org.wiremock.grpc.internal.GrpcHttpServerFactory;

@Beta(justification = "Incubating extension: https://github.com/wiremock/wiremock/issues/2383")
public class GrpcExtensionFactory implements ExtensionFactory {

  private List<URL> urls;

  public GrpcExtensionFactory() {
  }

  public GrpcExtensionFactory(List<URL> urls) {
    this.urls = urls;
  }

  @Override
  public List<Extension> create(WireMockServices services) {
    if(this.urls !=null){
       return List.of(new GrpcHttpServerFactory(this.urls));
    }
    return List.of(new GrpcHttpServerFactory(services.getStores().getBlobStore("grpc")));
  }
}
