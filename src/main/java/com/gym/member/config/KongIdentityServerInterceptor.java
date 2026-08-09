package com.gym.member.config;

import com.gym.common.grpc.interceptor.GrpcMethodRegistry;
import com.gym.common.grpc.security.RpcPolicyKind;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.Set;

public final class KongIdentityServerInterceptor implements ServerInterceptor {

    private static final Set<String> KONG_SANS = Set.of(
            "kong",
            "spiffe://gym.cluster.local/ns/default/sa/kong",
            "spiffe://gym.cluster.local/ns/gym-system/sa/kong");

    private final GrpcMethodRegistry methodRegistry;

    public KongIdentityServerInterceptor(GrpcMethodRegistry methodRegistry) {
        this.methodRegistry = methodRegistry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        var policy = methodRegistry.getPolicy(call.getMethodDescriptor().getFullMethodName());
        if (policy != null
                && policy.kind() != RpcPolicyKind.PUBLIC
                && policy.kind() != RpcPolicyKind.INTERNAL_WORKLOAD
                && !PeerCertificateIdentity.hasAllowedSan(call, KONG_SANS)) {
            call.close(Status.PERMISSION_DENIED.withDescription("Verified Kong identity is required"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}
