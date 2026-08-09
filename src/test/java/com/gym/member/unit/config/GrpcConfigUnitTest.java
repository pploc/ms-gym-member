package com.gym.member.unit.config;

import com.gym.common.grpc.security.WorkloadIdentityVerifier;
import com.gym.member.config.GrpcConfig;
import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcConfigUnitTest {

    private static final String GET_MEMBERSHIP_STATUS_BY_USER_ID =
            "member.v1.MemberService/GetMembershipStatusByUserId";
    private static final String VALIDATE_MEMBERSHIP = "member.v1.MemberService/ValidateMembership";
    private static final String LIST_MEMBERS_BY_STATUS = "member.v1.MemberService/ListMembersByStatus";

    private final GrpcConfig config = new GrpcConfig(
            mock(com.gym.member.member.adapter.in.grpc.MemberGrpcHandler.class),
            mock(com.gym.common.grpc.interceptor.AuthServerInterceptor.class),
            mock(com.gym.member.config.KongIdentityServerInterceptor.class),
            mock(com.gym.common.grpc.interceptor.ExceptionInterceptor.class),
            mock(com.gym.common.grpc.interceptor.LoggingInterceptor.class),
            mock(com.gym.common.grpc.interceptor.TracingInterceptor.class),
            mock(com.gym.common.grpc.interceptor.MetricsInterceptor.class),
            mock(com.gym.common.grpc.interceptor.ValidationInterceptor.class));

    private final WorkloadIdentityVerifier verifier = GrpcConfig.workloadIdentityVerifier();

    @Test
    void given_identifier_dns_san_on_status_by_user_id_when_verify_workload_identity_then_accepts() throws Exception {
        // given
        ServerCall<?, ?> call = callWithSan(GET_MEMBERSHIP_STATUS_BY_USER_ID, List.of(List.of(2, "ms-gym-identifier")));

        // when / then
        assertTrue(verifier.isVerified(call));
    }

    @Test
    void given_identifier_spiffe_san_on_status_by_user_id_when_verify_workload_identity_then_accepts() throws Exception {
        // given
        ServerCall<?, ?> call = callWithSan(
                GET_MEMBERSHIP_STATUS_BY_USER_ID,
                List.of(List.of(6, "spiffe://gym.cluster.local/ns/default/sa/ms-gym-identifier")));

        // when / then
        assertTrue(verifier.isVerified(call));
    }

    @Test
    void given_gym_system_identifier_spiffe_san_when_verify_workload_identity_then_accepts() throws Exception {
        // given
        ServerCall<?, ?> call = callWithSan(
                GET_MEMBERSHIP_STATUS_BY_USER_ID,
                List.of(List.of(6, "spiffe://gym.cluster.local/ns/gym-system/sa/ms-gym-identifier")));

        // when / then
        assertTrue(verifier.isVerified(call));
    }

    @Test
    void given_checkin_san_on_validate_membership_when_verify_workload_identity_then_accepts() throws Exception {
        // given
        ServerCall<?, ?> call = callWithSan(VALIDATE_MEMBERSHIP, List.of(List.of(2, "ms-gym-checkin")));

        // when / then
        assertTrue(verifier.isVerified(call));
    }

    @Test
    void given_notification_san_on_list_members_by_status_when_verify_workload_identity_then_accepts() throws Exception {
        // given
        ServerCall<?, ?> call = callWithSan(LIST_MEMBERS_BY_STATUS, List.of(List.of(2, "ms-gym-notification")));

        // when / then
        assertTrue(verifier.isVerified(call));
    }

    @Test
    void given_checkin_san_on_status_by_user_id_when_verify_workload_identity_then_rejects() throws Exception {
        // given
        ServerCall<?, ?> call = callWithSan(GET_MEMBERSHIP_STATUS_BY_USER_ID, List.of(List.of(2, "ms-gym-checkin")));

        // when / then
        assertFalse(verifier.isVerified(call));
    }

    @Test
    void given_identifier_san_on_validate_membership_when_verify_workload_identity_then_rejects() throws Exception {
        // given
        ServerCall<?, ?> call = callWithSan(VALIDATE_MEMBERSHIP, List.of(List.of(2, "ms-gym-identifier")));

        // when / then
        assertFalse(verifier.isVerified(call));
    }

    @Test
    void given_missing_tls_session_when_verify_workload_identity_then_rejects() {
        // given
        ServerCall<Object, Object> call = mock(ServerCall.class);
        doReturn(method(GET_MEMBERSHIP_STATUS_BY_USER_ID)).when(call).getMethodDescriptor();
        when(call.getAttributes()).thenReturn(Attributes.EMPTY);

        // when / then
        assertFalse(verifier.isVerified(call));
    }

    @Test
    void given_plaintext_without_explicit_test_opt_in_when_build_grpc_server_then_rejects() throws Exception {
        // given
        setField("tlsEnabled", false);
        setField("allowPlaintext", false);

        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, this::serverBuilder);

        // then
        assertTrue(exception.getMessage().contains("explicit test configuration"));
    }

    @Test
    void given_tls_without_certificate_materials_when_build_grpc_server_then_rejects() throws Exception {
        // given
        setField("tlsEnabled", true);
        setField("certificateChain", "");
        setField("privateKey", "");
        setField("clientCa", "");

        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, this::serverBuilder);

        // then
        assertTrue(exception.getMessage().contains("certificate-chain, private-key, and client-ca"));
    }

    @Test
    void given_wrong_or_non_identity_san_when_verify_workload_identity_then_rejects() throws Exception {
        // given
        ServerCall<?, ?> call = callWithSan(
                GET_MEMBERSHIP_STATUS_BY_USER_ID,
                List.of(List.of(2, "ms-gym-payment"), List.of(1, "ms-gym-identifier")));

        // when / then
        assertFalse(verifier.isVerified(call));
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

    private static ServerCall<?, ?> callWithSan(String fullMethod, List<List<?>> sans) throws Exception {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        doReturn(method(fullMethod)).when(call).getMethodDescriptor();

        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn((Collection) sans);

        SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(new Certificate[] {certificate});
        when(call.getAttributes())
                .thenReturn(Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build());
        return call;
    }

    private static MethodDescriptor<Object, Object> method(String fullMethod) {
        return MethodDescriptor.newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethod)
                .setRequestMarshaller(dummy())
                .setResponseMarshaller(dummy())
                .build();
    }

    private static MethodDescriptor.Marshaller<Object> dummy() {
        return new MethodDescriptor.Marshaller<>() {
            @Override
            public java.io.InputStream stream(Object value) {
                return java.io.InputStream.nullInputStream();
            }

            @Override
            public Object parse(java.io.InputStream stream) {
                return new Object();
            }
        };
    }
}
