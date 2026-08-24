package com.gym.member.payment.adapter.out.grpc;

import com.gym.member.config.MemberProperties;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import com.gym.proto.payment.v1.PaymentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PaymentGrpcClient implements AutoCloseable {

    private final ManagedChannel channel;
    private final PaymentServiceGrpc.PaymentServiceBlockingStub stub;
    private final MemberProperties properties;

    public PaymentGrpcClient(MemberProperties properties) {
        this.properties = properties;
        String target = properties.payment() != null ? properties.payment().target() : null;
        if (target == null || target.isBlank()) {
            this.channel = null;
            this.stub = null;
            return;
        }

        MemberProperties.PaymentProperties payment = properties.payment();
        if (payment.usePlaintext()) {
            this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        } else {
            try {
                NettyChannelBuilder builder = NettyChannelBuilder.forTarget(target)
                        .sslContext(GrpcSslContexts.forClient()
                                .keyManager(new File(payment.clientCert()), new File(payment.clientKey()))
                                .trustManager(new File(payment.serverCa()))
                                .build());
                if (payment.authority() != null && !payment.authority().isBlank()) {
                    builder.overrideAuthority(payment.authority());
                }
                this.channel = builder.build();
            } catch (SSLException e) {
                throw new IllegalStateException("Failed to configure Payment mTLS client", e);
            }
        }
        this.stub = PaymentServiceGrpc.newBlockingStub(channel);
    }

    public InitiatePaymentResponse initiatePayment(InitiatePaymentRequest request) {
        if (stub == null) {
            throw Status.UNAVAILABLE.withDescription("Payment service is not configured").asRuntimeException();
        }
        try {
            return stub.withDeadlineAfter(properties.payment().deadline().toMillis(), TimeUnit.MILLISECONDS)
                    .initiatePayment(request);
        } catch (StatusRuntimeException e) {
            log.warn("Payment service unavailable: {}", e.getStatus().getCode());
            throw e;
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null && !channel.isShutdown()) {
            log.info("Shutting down PaymentGrpcClient channel...");
            channel.shutdown();
        }
    }
}
