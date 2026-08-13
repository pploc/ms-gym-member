package com.gym.member.unit.config;

import com.gym.common.grpc.interceptor.GrpcMethodRegistry;
import com.gym.common.grpc.security.RpcPolicyKind;
import com.gym.member.config.KongIdentityServerInterceptor;
import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KongIdentityServerInterceptorUnitTest {

    private static final String END_USER_METHOD = "member.v1.MemberService/GetMember";
    private static final String INTERNAL_METHOD = "member.v1.MemberService/ValidateMembership";

    @Mock
    private GrpcMethodRegistry registry;

    @Mock
    private ServerCallHandler<Object, Object> next;

    @Mock
    private ServerCall.Listener<Object> listener;

    @Test
    void given_gateway_san_on_end_user_rpc_when_intercept_then_continues() throws Exception {
        // given
        when(registry.getPolicy(END_USER_METHOD))
                .thenReturn(new GrpcMethodRegistry.MethodPolicy(RpcPolicyKind.ROLE_RESTRICTED, new String[] {"CUSTOMER"}));
        ServerCall<Object, Object> call = callWithSan(END_USER_METHOD, "ms-gym-api-gateway");
        when(next.startCall(any(), any())).thenReturn(listener);
        KongIdentityServerInterceptor interceptor = new KongIdentityServerInterceptor(registry);

        // when
        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        // then
        assertNotNull(result);
        verify(next).startCall(any(), any());
        verify(call, never()).close(any(), any());
    }

    @Test
    void given_non_kong_san_on_end_user_rpc_when_intercept_then_permission_denied() throws Exception {
        // given
        when(registry.getPolicy(END_USER_METHOD))
                .thenReturn(new GrpcMethodRegistry.MethodPolicy(RpcPolicyKind.ROLE_RESTRICTED, new String[] {"CUSTOMER"}));
        ServerCall<Object, Object> call = callWithSan(END_USER_METHOD, "ms-gym-checkin");
        KongIdentityServerInterceptor interceptor = new KongIdentityServerInterceptor(registry);

        // when
        interceptor.interceptCall(call, new Metadata(), next);

        // then
        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any(Metadata.class));
        assertEquals(Status.Code.PERMISSION_DENIED, statusCaptor.getValue().getCode());
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void given_internal_workload_rpc_when_intercept_then_skips_kong_check() {
        // given
        when(registry.getPolicy(INTERNAL_METHOD))
                .thenReturn(new GrpcMethodRegistry.MethodPolicy(RpcPolicyKind.INTERNAL_WORKLOAD, new String[0]));
        ServerCall<Object, Object> call = callWithMethod(INTERNAL_METHOD);
        when(next.startCall(any(), any())).thenReturn(listener);
        KongIdentityServerInterceptor interceptor = new KongIdentityServerInterceptor(registry);

        // when
        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        // then
        assertNotNull(result);
        verify(next).startCall(any(), any());
        verify(call, never()).close(any(), any());
    }

    private static ServerCall<Object, Object> callWithMethod(String fullMethod) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> method = MethodDescriptor.newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethod)
                .setRequestMarshaller(dummy())
                .setResponseMarshaller(dummy())
                .build();
        doReturn(method).when(call).getMethodDescriptor();
        return call;
    }

    private static ServerCall<Object, Object> callWithSan(String fullMethod, String san) throws Exception {
        ServerCall<Object, Object> call = callWithMethod(fullMethod);

        SSLSession session = mock(SSLSession.class);
        X509Certificate cert = mock(X509Certificate.class);
        when(session.getPeerCertificates()).thenReturn(new Certificate[] {cert});
        when(cert.getSubjectAlternativeNames()).thenReturn((Collection) List.of(List.of(2, san)));
        when(call.getAttributes())
                .thenReturn(Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build());
        return call;
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
