package com.gym.member.config;

import com.gym.member.adapter.in.grpc.MemberGrpcHandler;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.io.IOException;

import io.grpc.protobuf.services.ProtoReflectionService;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GrpcConfig {

    private final MemberGrpcHandler memberGrpcHandler;

    @Value("${grpc.server.port:50051}")
    private int grpcPort;

    private Server server;

    @EventListener(ApplicationReadyEvent.class)
    public void startGrpcServer() throws IOException {
        server = ServerBuilder.forPort(grpcPort)
                .addService(memberGrpcHandler)
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();
        log.info("gRPC server started on port {}", grpcPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server...");
            if (server != null) {
                server.shutdown();
            }
        }));
    }
}
