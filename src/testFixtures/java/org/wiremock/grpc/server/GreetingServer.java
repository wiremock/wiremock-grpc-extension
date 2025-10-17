package org.wiremock.grpc.server;

import com.example.grpc.GreetingServiceGrpc;
import com.example.grpc.request.HelloRequest;
import com.example.grpc.response.HelloResponse;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GreetingServer {

    private final int port;
    private Server server;

    public GreetingServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = ServerBuilder.forPort(port)
                    .addService(new GreetingServiceImpl())
                    .build()
                    .start();
            System.out.println("gRPC server started on port " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void stop() {
        if (server != null) {
            server.shutdown();
            System.out.println("gRPC stopped");
        }
    }

    static class GreetingServiceImpl extends GreetingServiceGrpc.GreetingServiceImplBase {

        @Override
        public void greeting(HelloRequest request, io.grpc.stub.StreamObserver<HelloResponse> responseObserver) {
            String name = request.getName();
            HelloResponse response = HelloResponse.newBuilder()
                    .setGreeting("Hello from GRPC proxy, " + name)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
