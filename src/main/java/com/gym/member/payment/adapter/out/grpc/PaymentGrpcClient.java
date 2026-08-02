package com.gym.member.payment.adapter.out.grpc;

import com.gym.member.config.MemberProperties;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import com.gym.proto.payment.v1.PaymentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PaymentGrpcClient implements AutoCloseable {

    private final ManagedChannel channel;
    private final PaymentServiceGrpc.PaymentServiceBlockingStub stub;
    private final MemberProperties properties;

    public PaymentGrpcClient(MemberProperties properties) {
        this.properties = properties;
        String target = properties.payment().target();
        if (target == null || target.isBlank()) {
            this.channel = null;
            this.stub = null;
        } else {
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target);
            if (properties.payment().usePlaintext()) {
                builder.usePlaintext();
            }
            this.channel = builder.build();
            this.stub = PaymentServiceGrpc.newBlockingStub(channel);
        }
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
