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

import com.github.tomakehurst.wiremock.common.Exceptions;
import com.github.tomakehurst.wiremock.store.BlobStore;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlobProtoDescriptorStore implements ProtoDescriptorStore {
  private final BlobStore blobStore;

  public BlobProtoDescriptorStore(BlobStore blobStore) {
    this.blobStore = blobStore;
  }

  @Override
  public List<Descriptors.FileDescriptor> loadAllFileDescriptors() {
    List<Descriptors.FileDescriptor> fileDescriptors = new ArrayList<>();
    blobStore
        .getAllKeys()
        .filter(key -> key.endsWith(".dsc") || key.endsWith(".desc"))
        .map(
            key ->
                blobStore
                    .get(key)
                    .map(
                        data ->
                            Exceptions.uncheck(
                                () -> DescriptorProtos.FileDescriptorSet.parseFrom(data),
                                DescriptorProtos.FileDescriptorSet.class)))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .flatMap(fileDescriptorSet -> fileDescriptorSet.getFileList().stream())
        .forEach(
            fileDescriptorProto ->
                Exceptions.uncheck(
                    () ->
                        fileDescriptors.add(
                            Descriptors.FileDescriptor.buildFrom(
                                fileDescriptorProto,
                                fileDescriptors.toArray(Descriptors.FileDescriptor[]::new),
                                true))));
    return fileDescriptors;
  }
}
