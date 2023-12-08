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
package org.wiremock.grpc.internal;

import com.github.tomakehurst.wiremock.http.Response;
import java.util.concurrent.TimeUnit;

public class Delays {

  public static void delayIfRequired(Response response) {
    final long delayMillis = response.getInitialDelay();
    if (delayMillis > 0) {
      try {
        TimeUnit.MILLISECONDS.sleep(delayMillis);
      } catch (InterruptedException var4) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
