---
description: >
  WireMock extension for gRPC.
og_image: solutions/grpc/wiremock_grpc_opengraph.png
hero: solutions/grpc/header.png
---

# Mocking gRPC services

The [WireMock extension for gRPC](https://github.com/wiremock/wiremock-grpc-extension), compatible with WireMock 3.2.0+, supports mocking of gRPC services.

To set up the extension for use, you:

- add the extension JAR dependency to your project.
- create a root directory for WireMock e.g. `src/test/resources/wiremock` with a subdirectory under it, named `grpc`.
- copy the descriptor files (*.dsc, *.desc) generated by `protoc` from your `.proto` files into the `grpc` subdirectory.

For details, see [Setup](setup) below.

At runtime, the extension:

- scans for descriptor files (generated from the service's `.proto` files) in the `grpc` subdirectory of WireMock's root.
- using the descriptor file, it converts incoming messages to JSON before passing them to WireMock's core stubbing system, which allows the existing JSON matchers to be used when matching requests.
- converts JSON responses back into proto messages so that all of WireMock's response definition features including templating can be used.

<img src="/images/solutions/grpc/intro_schema.png" alt="WireMock and gRPC schema" style="width: 100%; height: auto; margin-top: 1em;"/>

The extension also adds a Java DSL that works with the Java classes generated by `protoc`, while also providing a more gRPC idiomatic way of defining stubs.

## Java usage

### Setup

Add the extension JAR dependency to your project:

Gradle:

  ```gradle
  implementation 'org.wiremock:wiremock-grpc-extension:{{ versions.grpc_extension_version }}'
  ```

Maven:

  ```xml
  <dependency>
      <groupId>org.wiremock</groupId>
      <artifactId>wiremock-grpc-extension</artifactId>
      <version>{{ versions.grpc_extension_version }}</version>
  </dependency>
  ```

Create a root directory for WireMock, typically `src/test/resources/wiremock`, and create a subdirectory in it named `grpc`.

Copy the descriptor files generated by `protoc` from your `.proto` files into the `grpc` subdirectory.

Initialise WireMock server with the extension enabled and the root directory set to the path created in the previous steps:

```java
// Same config object also for the JUnit 4 rule or JUnit 5 extension
WireMockServer wm = new WireMockServer(wireMockConfig()
        .dynamicPort()
        .withRootDirectory("src/test/resources/wiremock")
        .extensions(new GrpcExtensionFactory())
));
```

Initialise a service class for the gRPC service you want to mock (this must be defined in the `.proto` file you compiled to a descriptor):

```java
WireMockGrpcService mockGreetingService =
    new WireMockGrpcService(
        new WireMock(wm.getPort()),
        "com.example.grpc.GreetingService"
    );
```

### Stubbing via JSON matching + responses

To specify request criteria and response data using JSON:

```java
mockGreetingService.stubFor(
    method("greeting")
        .withRequestMessage(equalToJson("{ \"name\":  \"Tom\" }"))
        .willReturn(json("{ "greeting": "Hi Tom from JSON" }")));
```

Or, with a templated response:

{% raw %}

```java
mockGreetingService.stubFor(
    method("greeting")
        .withRequestMessage(equalToJson("{ \"name\":  \"${json-unit.any-string}\" }"))
        .willReturn(
            jsonTemplate(
                "{ \"greeting\": \"Hello {{jsonPath request.body '$.name'}}\" }")));
```

{% endraw %}

### Stubbing via Java message objects

Matching and stubbing in the Java DSL can also be specified using the Java classes generated by `protoc`:

```java
mockGreetingService.stubFor(
    method("greeting")
        .withRequestMessage(equalToMessage(HelloRequest.newBuilder().setName("Tom")))
        .willReturn(message(HelloResponse.newBuilder().setGreeting("OK"))));
```

### Non-OK responses

You can return gRPC error codes instead of an OK response:

```java
mockGreetingService.stubFor(
    method("greeting")
        .withRequestMessage(equalToMessage(
            HelloRequest.newBuilder().setName("Prereq failure")
        ))
        .willReturn(Status.FAILED_PRECONDITION, "Failed on some prerequisite"));
```

## More examples

For a more complete set of examples, see the [Java demo project](https://github.com/wiremock/wiremock-grpc-demos/tree/main/java).


## Standalone usage

### Setup

Download the <a id="wiremock-standalone-download" href="https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/{{ versions.wiremock_version }}/wiremock-standalone-{{ versions.wiremock_version }}.jar">standalone JAR</a> at version 3.2.0 or above
and the <a id="wiremock-standalone-download" href="https://repo1.maven.org/maven2/org/wiremock/wiremock-grpc-extension-standalone/{{ versions.grpc_extension_version }}/wiremock-grpc-extension-standalone-{{ versions.grpc_extension_version }}.jar">gRPC extension JAR</a> into your working directory.

Create a WireMock data directory with two subdirectories; one for stub mappings, and another for descriptor files:

```bash
mkdir -p wiremock wiremock/mappings wiremock/grpc
```

Compile your proto files into descriptors:

```bash
protoc --descriptor_set_out wiremock/grpc/services.dsc ExampleServices.proto
```

Run WireMock, with both directories you just created on the classpath:

```bash
java -cp wiremock-standalone-{{ versions.wiremock_version }}.jar:wiremock-grpc-extension-standalone-{{ versions.grpc_extension_version }}.jar \
  wiremock.Run \
  --root-dir wiremock
```

### Stubbing

gRPC stubs are defined using WireMock's standard JSON format. Requests should always be matched with a `POST` method and a URL path of `/<fully-qualified service name>/<method name>`.

```json
{
  "request" : {
    "urlPath" : "/com.example.grpc.GreetingService/greeting",
    "method" : "POST",
    "bodyPatterns" : [{
      "equalToJson" : "{ \"name\":  \"Tom\" }"
    }]
  },
  "response" : {
    "status" : 200,
    "body" : "{\n  \"greeting\": \"Hi Tom\"\n}",
    "headers" : {
      "grpc-status-name" : "OK"
    }
  }
}
```

## Reloading gRPC descriptor files

If you plan to update your gRPC descriptor files at runtime, you can inform WireMock to reload all file descriptors via a POST to the admin API endpoint `/__admin/ext/grpc/reset`.

## More Demos

For more see the [standalone demo project](https://github.com/wiremock/wiremock-grpc-demos/tree/main/standalone).
