package com.gym.member.unit.grpc;

import com.gym.member.config.MemberProperties;
import com.gym.member.plans.adapter.out.grpc.PlansGrpcClient;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlansGrpcClientUnitTest {

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
                "certs/local/client-identifier.crt",
                "certs/local/client-identifier.key",
                "certs/local/ca.crt",
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
                "certs/local/missing-client.crt",
                "certs/local/missing-client.key",
                "certs/local/missing-ca.crt",
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
                "certs/local/client-identifier.crt",
                "certs/local/client-identifier.key",
                "certs/local/ca.crt",
                "");
        MemberProperties properties = new MemberProperties(null, null, null, null, plansProps, false);

        PlansGrpcClient client = new PlansGrpcClient(properties);
        client.close();
    }
}
