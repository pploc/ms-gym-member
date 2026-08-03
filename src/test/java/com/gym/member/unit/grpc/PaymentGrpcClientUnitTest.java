package com.gym.member.unit.grpc;

import com.gym.member.config.MemberProperties;
import com.gym.member.payment.adapter.out.grpc.PaymentGrpcClient;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class PaymentGrpcClientUnitTest {

    @Test
    void givenUnconfiguredTarget_whenInitiatePayment_throwsStatusRuntimeException() {
        MemberProperties.PaymentProperties paymentProps = new MemberProperties.PaymentProperties("", Duration.ofSeconds(3), true);
        MemberProperties properties = new MemberProperties(null, null, null, paymentProps, false);

        PaymentGrpcClient client = new PaymentGrpcClient(properties);
        assertThrows(StatusRuntimeException.class, () -> client.initiatePayment(InitiatePaymentRequest.getDefaultInstance()));
        client.close();
    }

    @Test
    void givenConfiguredTarget_whenConstructedAndClosed_createsChannelAndShutdowns() {
        MemberProperties.PaymentProperties paymentProps = new MemberProperties.PaymentProperties("localhost:50052", Duration.ofSeconds(3), true);
        MemberProperties properties = new MemberProperties(null, null, null, paymentProps, false);

        PaymentGrpcClient client = new PaymentGrpcClient(properties);
        client.close();
        client.close(); // idempotent close
    }
}
