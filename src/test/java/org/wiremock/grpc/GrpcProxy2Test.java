package org.wiremock.grpc;

import com.example.grpc.GreetingServiceGrpc;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.grpc.client.GreetingsClient;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import org.wiremock.grpc.server.GreetingServer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class GrpcProxy2Test {

    WireMockGrpcService mockGreetingService;
    ManagedChannel channel;
    GreetingsClient greetingsClient;
    WireMock wireMock;
    GreetingServer greetingServer;

    @RegisterExtension
    public static WireMockExtension wm =
            WireMockExtension.newInstance()
                    .options(
                            wireMockConfig()
                                    .dynamicPort()
                                    .withRootDirectory("src/test/resources/wiremock")
                                    .extensions(new GrpcExtensionFactory()))
                    .build();

    @BeforeEach
    void init() {
        wireMock = wm.getRuntimeInfo().getWireMock();
        mockGreetingService = new WireMockGrpcService(wireMock, GreetingServiceGrpc.SERVICE_NAME);

        channel = ManagedChannelBuilder.forAddress("localhost", wm.getPort()).usePlaintext().build();
        greetingsClient = new GreetingsClient(channel);
        greetingServer = new GreetingServer(5088);
        greetingServer.start();
    }

    @AfterEach
    void tearDown() {
        channel.shutdown();
        greetingServer.stop();
    }

    @Test
    void withProxy() {

        wm.stubFor(
                post(urlPathEqualTo("/com.example.grpc.GreetingService/greeting"))
                        .willReturn(
                                aResponse()
                                        .proxiedFrom("http://localhost:5088")));

        String greeting = greetingsClient.greet("Tommy");

        assertThat(greeting, is("Hello from GRPC proxy, Tommy"));
    }

}
