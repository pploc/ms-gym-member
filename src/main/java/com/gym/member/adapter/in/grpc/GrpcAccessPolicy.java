package com.gym.member.adapter.in.grpc;

import com.gym.common.error.ForbiddenException;
import com.gym.common.grpc.security.GrpcSecurityContext;
import com.gym.member.member.domain.dto.MemberDto;

import java.util.Objects;

public final class GrpcAccessPolicy {

    private GrpcAccessPolicy() {}

    public static void requireSelf(MemberDto member) {
        if (isSuperAdmin()) {
            return;
        }
        if (!Objects.equals(GrpcSecurityContext.getUserId(), member.userId().toString())) {
            throw new ForbiddenException("Member access is outside the authenticated user scope");
        }
    }

    private static boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(GrpcSecurityContext.getRole());
    }
}
