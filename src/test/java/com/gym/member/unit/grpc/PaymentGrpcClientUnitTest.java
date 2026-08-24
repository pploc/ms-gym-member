package com.gym.member.unit.grpc;

import com.gym.member.config.MemberProperties;
import com.gym.member.payment.adapter.out.grpc.PaymentGrpcClient;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentGrpcClientUnitTest {

    // Checked-in PEMs under src/test/resources (local certs/ is gitignored).
    private static final String TEST_CLIENT_CERT = "src/test/resources/mtls/client.crt";
    private static final String TEST_CLIENT_KEY = "src/test/resources/mtls/client.key";
    private static final String TEST_CA_CERT = "src/test/resources/mtls/ca.crt";

    @Test
    void givenUnconfiguredTarget_whenInitiatePayment_thenThrowsUnavailable() {
        MemberProperties.PaymentProperties paymentProps = paymentProperties("", true, "ms-gym-payment");
        MemberProperties properties = new MemberProperties(null, null, null, paymentProps, null, false);

        PaymentGrpcClient client = new PaymentGrpcClient(properties);
        assertThrows(StatusRuntimeException.class, () -> client.initiatePayment(InitiatePaymentRequest.getDefaultInstance()));
        client.close();
    }

    @Test
    void givenNullPaymentProperties_whenInitiatePayment_thenThrowsUnavailable() {
        MemberProperties properties = new MemberProperties(null, null, null, null, null, false);

        PaymentGrpcClient client = new PaymentGrpcClient(properties);
        assertThrows(StatusRuntimeException.class, () -> client.initiatePayment(InitiatePaymentRequest.getDefaultInstance()));
        assertDoesNotThrow(client::close);
    }

    @Test
    void givenConfiguredPlaintextTarget_whenConstructedAndClosed_thenCreatesChannelAndShutsDown() {
        MemberProperties.PaymentProperties paymentProps = paymentProperties("localhost:50052", true, "ms-gym-payment");
        MemberProperties properties = new MemberProperties(null, null, null, paymentProps, null, false);

        PaymentGrpcClient client = new PaymentGrpcClient(properties);
        client.close();
        client.close();
    }

    @Test
    void givenConfiguredMtlsTargetWithLocalCerts_whenInitiatePayment_thenPropagatesUnavailable() {
        MemberProperties.PaymentProperties paymentProps = paymentProperties("localhost:59999", false, "ms-gym-payment");
        MemberProperties properties = new MemberProperties(null, null, null, paymentProps, null, false);

        PaymentGrpcClient client = new PaymentGrpcClient(properties);
        assertThrows(StatusRuntimeException.class, () -> client.initiatePayment(InitiatePaymentRequest.getDefaultInstance()));
        client.close();
    }

    @Test
    void givenInvalidMtlsCertPaths_whenConstructed_thenThrowsRuntimeException() {
        MemberProperties.PaymentProperties paymentProps = new MemberProperties.PaymentProperties(
                "localhost:50052",
                Duration.ofSeconds(3),
                false,
                "src/test/resources/mtls/missing-client.crt",
                "src/test/resources/mtls/missing-client.key",
                "src/test/resources/mtls/missing-ca.crt",
                "ms-gym-payment");
        MemberProperties properties = new MemberProperties(null, null, null, paymentProps, null, false);

        assertThrows(RuntimeException.class, () -> new PaymentGrpcClient(properties));
    }

    @Test
    void givenMtlsWithoutAuthority_whenConstructedAndClosed_thenCreatesChannel() {
        MemberProperties.PaymentProperties paymentProps = paymentProperties("localhost:59998", false, "");
        MemberProperties properties = new MemberProperties(null, null, null, paymentProps, null, false);

        PaymentGrpcClient client = new PaymentGrpcClient(properties);
        assertDoesNotThrow(client::close);
    }

    private static MemberProperties.PaymentProperties paymentProperties(String target, boolean usePlaintext, String authority) {
        return new MemberProperties.PaymentProperties(
                target,
                Duration.ofMillis(100),
                usePlaintext,
                TEST_CLIENT_CERT,
                TEST_CLIENT_KEY,
                TEST_CA_CERT,
                authority);
    }
}
