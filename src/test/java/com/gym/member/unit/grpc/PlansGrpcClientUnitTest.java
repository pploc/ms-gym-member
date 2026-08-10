package com.gym.member.unit.grpc;

import com.gym.member.config.MemberProperties;
import com.gym.member.plans.adapter.out.grpc.PlansGrpcClient;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlansGrpcClientUnitTest {

    // Checked-in PEMs under src/test/resources (local certs/ is gitignored).
    private static final String TEST_CLIENT_CERT = "src/test/resources/mtls/client.crt";
    private static final String TEST_CLIENT_KEY = "src/test/resources/mtls/client.key";
    private static final String TEST_CA_CERT = "src/test/resources/mtls/ca.crt";

    @Test
    void givenUnconfiguredTarget_whenResolvePurchasablePlan_thenThrowsUnavailable() {
        MemberProperties.PlansProperties plansProps =
                new MemberProperties.PlansProperties("", Duration.ofSeconds(3), true, "", "", "", "ms-gym-plans");
        MemberProperties properties = new MemberProperties(null, null, null, null, plansProps, false);

        PlansGrpcClient client = new PlansGrpcClient(properties);
        assertThrows(StatusRuntimeException.class, () -> client.resolvePurchasablePlan("plan-1", "gym-1"));
        client.close();
    }

    @Test
    void givenNullPlansProperties_whenResolvePurchasablePlan_thenThrowsUnavailable() {
        MemberProperties properties = new MemberProperties(null, null, null, null, null, false);

        PlansGrpcClient client = new PlansGrpcClient(properties);
        assertThrows(StatusRuntimeException.class, () -> client.resolvePurchasablePlan("plan-1", "gym-1"));
        assertDoesNotThrow(client::close);
    }

    @Test
    void givenConfiguredPlaintextTarget_whenConstructedAndClosed_thenCreatesChannelAndShutdowns() {
        MemberProperties.PlansProperties plansProps = new MemberProperties.PlansProperties(
                "localhost:50053", Duration.ofSeconds(3), true, "", "", "", "ms-gym-plans");
        MemberProperties properties = new MemberProperties(null, null, null, null, plansProps, false);

        PlansGrpcClient client = new PlansGrpcClient(properties);
        client.close();
        client.close();
    }

    @Test
    void givenConfiguredMtlsTargetWithLocalCerts_whenResolvePurchasablePlan_thenPropagatesUnavailable() {
        MemberProperties.PlansProperties plansProps = new MemberProperties.PlansProperties(
                "localhost:59999",
                Duration.ofMillis(100),
                false,
                TEST_CLIENT_CERT,
                TEST_CLIENT_KEY,
                TEST_CA_CERT,
                "ms-gym-plans");
        MemberProperties properties = new MemberProperties(null, null, null, null, plansProps, false);

        PlansGrpcClient client = new PlansGrpcClient(properties);
        assertThrows(StatusRuntimeException.class, () -> client.resolvePurchasablePlan("plan-1", "gym-1"));
        client.close();
    }

    @Test
    void givenInvalidMtlsCertPaths_whenConstructed_thenThrowsRuntimeException() {
        MemberProperties.PlansProperties plansProps = new MemberProperties.PlansProperties(
                "localhost:50053",
                Duration.ofSeconds(3),
                false,
                "src/test/resources/mtls/missing-client.crt",
                "src/test/resources/mtls/missing-client.key",
                "src/test/resources/mtls/missing-ca.crt",
                "ms-gym-plans");
        MemberProperties properties = new MemberProperties(null, null, null, null, plansProps, false);

        assertThrows(RuntimeException.class, () -> new PlansGrpcClient(properties));
    }

    @Test
    void givenMtlsWithoutAuthority_whenConstructedAndClosed_thenCreatesChannel() {
        MemberProperties.PlansProperties plansProps = new MemberProperties.PlansProperties(
                "localhost:59998",
                Duration.ofMillis(100),
                false,
                TEST_CLIENT_CERT,
                TEST_CLIENT_KEY,
                TEST_CA_CERT,
                "");
        MemberProperties properties = new MemberProperties(null, null, null, null, plansProps, false);

        PlansGrpcClient client = new PlansGrpcClient(properties);
        client.close();
    }
}
