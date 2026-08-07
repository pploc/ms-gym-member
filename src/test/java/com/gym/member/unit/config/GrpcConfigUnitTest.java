package com.gym.member.unit.config;

import com.gym.common.grpc.security.WorkloadIdentityVerifier;
import com.gym.member.config.GrpcConfig;
import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.ServerCall;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcConfigUnitTest {

    private final GrpcConfig config = new GrpcConfig(
            mock(com.gym.member.member.adapter.in.grpc.MemberGrpcHandler.class),
            mock(com.gym.common.grpc.interceptor.AuthServerInterceptor.class),
            mock(com.gym.common.grpc.interceptor.ExceptionInterceptor.class),
            mock(com.gym.common.grpc.interceptor.LoggingInterceptor.class),
            mock(com.gym.common.grpc.interceptor.TracingInterceptor.class),
            mock(com.gym.common.grpc.interceptor.MetricsInterceptor.class)
    );

    @Test
    void givenIdentifierDnsSan_whenVerifyWorkloadIdentity_thenAccepts() throws Exception {
        // Given
        WorkloadIdentityVerifier verifier = config.workloadIdentityVerifier();
        ServerCall<?, ?> call = callWithSans(List.of(List.of(2, "ms-gym-identifier")));

        // When
        boolean verified = verifier.isVerified(call);

        // Then
        assertTrue(verified);
    }

    @Test
    void givenIdentifierSpiffeSan_whenVerifyWorkloadIdentity_thenAccepts() throws Exception {
        // Given
        WorkloadIdentityVerifier verifier = config.workloadIdentityVerifier();
        ServerCall<?, ?> call = callWithSans(List.of(
                List.of(6, "spiffe://gym.cluster.local/ns/default/sa/ms-gym-identifier")
        ));

        // When
        boolean verified = verifier.isVerified(call);

        // Then
        assertTrue(verified);
    }

    @Test
    void givenGymSystemIdentifierSpiffeSan_whenVerifyWorkloadIdentity_thenAccepts() throws Exception {
        // Given
        WorkloadIdentityVerifier verifier = config.workloadIdentityVerifier();
        ServerCall<?, ?> call = callWithSans(List.of(
                List.of(6, "spiffe://gym.cluster.local/ns/gym-system/sa/ms-gym-identifier")
        ));

        // When
        boolean verified = verifier.isVerified(call);

        // Then
        assertTrue(verified);
    }

    @Test
    void givenMissingTlsSession_whenVerifyWorkloadIdentity_thenRejects() {
        // Given
        WorkloadIdentityVerifier verifier = config.workloadIdentityVerifier();
        ServerCall<?, ?> call = mock(ServerCall.class);
        when(call.getAttributes()).thenReturn(Attributes.EMPTY);

        // When
        boolean verified = verifier.isVerified(call);

        // Then
        assertFalse(verified);
    }

    @Test
    void givenPlaintextWithoutExplicitTestOptIn_whenBuildGrpcServer_thenRejects() throws Exception {
        // Given
        setField("tlsEnabled", false);
        setField("allowPlaintext", false);

        // When
        IllegalStateException exception = assertThrows(IllegalStateException.class, this::serverBuilder);

        // Then
        assertTrue(exception.getMessage().contains("explicit test configuration"));
    }

    @Test
    void givenTlsWithoutCertificateMaterials_whenBuildGrpcServer_thenRejects() throws Exception {
        // Given
        setField("tlsEnabled", true);
        setField("certificateChain", "");
        setField("privateKey", "");
        setField("clientCa", "");

        // When
        IllegalStateException exception = assertThrows(IllegalStateException.class, this::serverBuilder);

        // Then
        assertTrue(exception.getMessage().contains("certificate-chain, private-key, and client-ca"));
    }

    @Test
    void givenWrongOrNonIdentitySan_whenVerifyWorkloadIdentity_thenRejects() throws Exception {
        // Given
        WorkloadIdentityVerifier verifier = config.workloadIdentityVerifier();
        ServerCall<?, ?> call = callWithSans(List.of(
                List.of(2, "ms-gym-payment"),
                List.of(1, "ms-gym-identifier")
        ));

        // When
        boolean verified = verifier.isVerified(call);

        // Then
        assertFalse(verified);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = GrpcConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(config, value);
    }

    private void serverBuilder() throws Exception {
        Method method = GrpcConfig.class.getDeclaredMethod("serverBuilder");
        method.setAccessible(true);
        try {
            method.invoke(config);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static ServerCall<?, ?> callWithSans(List<List<?>> sans) throws Exception {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn((java.util.Collection) sans);

        SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(new Certificate[]{certificate});

        ServerCall<?, ?> call = mock(ServerCall.class);
        when(call.getAttributes()).thenReturn(
                Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build()
        );
        return call;
    }
}
