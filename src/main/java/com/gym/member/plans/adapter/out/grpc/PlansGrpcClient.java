package com.gym.member.plans.adapter.out.grpc;

import com.gym.member.config.MemberProperties;
import com.gym.proto.plans.v1.PlansServiceGrpc;
import com.gym.proto.plans.v1.ResolvePurchasablePlanRequest;
import com.gym.proto.plans.v1.ResolvedPlanResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PlansGrpcClient implements AutoCloseable {

    private final ManagedChannel channel;
    private final PlansServiceGrpc.PlansServiceBlockingStub stub;
    private final MemberProperties properties;

    public PlansGrpcClient(MemberProperties properties) {
        this.properties = properties;
        String target = properties.plans() != null ? properties.plans().target() : null;
        if (target == null || target.isBlank()) {
            this.channel = null;
            this.stub = null;
            return;
        }

        MemberProperties.PlansProperties plans = properties.plans();
        if (plans.usePlaintext()) {
            this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        } else {
            try {
                NettyChannelBuilder builder = NettyChannelBuilder.forTarget(target)
                        .sslContext(GrpcSslContexts.forClient()
                                .keyManager(new File(plans.clientCert()), new File(plans.clientKey()))
                                .trustManager(new File(plans.serverCa()))
                                .build());
                if (plans.authority() != null && !plans.authority().isBlank()) {
                    builder.overrideAuthority(plans.authority());
                }
                this.channel = builder.build();
            } catch (SSLException e) {
                throw new IllegalStateException("Failed to configure Plans mTLS client", e);
            }
        }
        this.stub = PlansServiceGrpc.newBlockingStub(channel);
    }

    public ResolvedPlanResponse resolvePurchasablePlan(String planId, String gymId) {
        if (stub == null) {
            throw Status.UNAVAILABLE.withDescription("Plans service is not configured").asRuntimeException();
        }
        try {
            return stub.withDeadlineAfter(properties.plans().deadline().toMillis(), TimeUnit.MILLISECONDS)
                    .resolvePurchasablePlan(ResolvePurchasablePlanRequest.newBuilder()
                            .setPlanId(planId)
                            .setGymId(gymId)
                            .build());
        } catch (StatusRuntimeException e) {
            log.warn("Plans ResolvePurchasablePlan failed: {}", e.getStatus().getCode());
            throw e;
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null && !channel.isShutdown()) {
            log.info("Shutting down PlansGrpcClient channel...");
            channel.shutdown();
        }
    }
}
