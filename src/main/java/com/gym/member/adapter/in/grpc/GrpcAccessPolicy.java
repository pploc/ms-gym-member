package com.gym.member.adapter.in.grpc;

import com.gym.common.error.ForbiddenException;
import com.gym.common.grpc.security.GrpcSecurityContext;
import com.gym.member.member.domain.dto.MemberDto;

import java.util.Collection;
import java.util.Objects;

public final class GrpcAccessPolicy {

    private GrpcAccessPolicy() {}

    public static void requireSelf(MemberDto member) {
        String role = GrpcSecurityContext.getRole();
        if (isSuperAdmin() || "ADMIN".equals(role) || isServiceRole(role)) {
            return;
        }
        if (!Objects.equals(GrpcSecurityContext.getUserId(), member.userId().toString())) {
            throw new ForbiddenException("Member access is outside the authenticated user scope");
        }
    }

    private static boolean isServiceRole(String role) {
        return role != null && role.endsWith("_SERVICE");
    }

    public static void requireGym(String gymId) {
        if (isSuperAdmin()) {
            return;
        }
        if (!Objects.equals(GrpcSecurityContext.getGymId(), gymId)) {
            throw new ForbiddenException("Gym access is outside the authenticated scope");
        }
    }

    public static void requireGymIds(Collection<String> gymIds) {
        if (isSuperAdmin()) {
            return;
        }
        String claimGymId = GrpcSecurityContext.getGymId();
        if (claimGymId == null || (gymIds != null && !gymIds.isEmpty() && !gymIds.stream().allMatch(claimGymId::equals))) {
            throw new ForbiddenException("Requested gyms are outside the authenticated scope");
        }
    }

    public static void requireServiceGym(String gymId) {
        requireGym(gymId);
    }

    private static boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(GrpcSecurityContext.getRole());
    }
}
