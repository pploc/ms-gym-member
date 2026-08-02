package com.gym.member.config;

import com.gym.common.grpc.interceptor.AuthServerInterceptor;
import com.gym.common.grpc.interceptor.ExceptionInterceptor;
import com.gym.common.grpc.interceptor.LoggingInterceptor;
import com.gym.common.grpc.interceptor.MetricsInterceptor;
import com.gym.common.grpc.interceptor.TracingInterceptor;
import com.gym.member.member.adapter.in.grpc.MemberGrpcHandler;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.ServerInterceptor;
import io.grpc.protobuf.services.ProtoReflectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.util.List;

@Slf4j
@Configuration
public class GrpcConfig {

    private final MemberGrpcHandler memberGrpcHandler;
    private final List<ServerInterceptor> interceptors;

    @Value("${grpc.server.port:50051}")
    private int grpcPort;

    private Server server;

    public GrpcConfig(
            MemberGrpcHandler memberGrpcHandler,
            AuthServerInterceptor authServerInterceptor,
            ExceptionInterceptor exceptionInterceptor,
            LoggingInterceptor loggingInterceptor,
            TracingInterceptor tracingInterceptor,
            MetricsInterceptor metricsInterceptor) {
        this.memberGrpcHandler = memberGrpcHandler;
        this.interceptors = List.of(
                tracingInterceptor,
                loggingInterceptor,
                metricsInterceptor,
                exceptionInterceptor,
                authServerInterceptor
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startGrpcServer() throws IOException {
        server = ServerBuilder.forPort(grpcPort)
                .addService(ServerInterceptors.intercept(memberGrpcHandler, interceptors))
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
