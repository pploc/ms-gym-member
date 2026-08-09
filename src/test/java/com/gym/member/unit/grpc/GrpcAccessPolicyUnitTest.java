package com.gym.member.unit.grpc;

import com.gym.common.error.ForbiddenException;
import com.gym.common.grpc.security.GrpcSecurityContext;
import com.gym.common.grpc.security.UserClaims;
import com.gym.member.adapter.in.grpc.GrpcAccessPolicy;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;
import io.grpc.Context;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GrpcAccessPolicyUnitTest {

    @Test
    void givenMismatchUserId_whenRequireSelf_throwsForbiddenException() {
        UserClaims claims = new UserClaims("user-1", "MEMBER", "gym-1", null);
        Context ctx = Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims);
        ctx.run(() -> {
            MemberDto member = new MemberDto(UUID.randomUUID(), UUID.randomUUID(), "Name", null, null, null, MembershipStatus.ACTIVE, Instant.now(), Instant.now());
            assertThrows(ForbiddenException.class, () -> GrpcAccessPolicy.requireSelf(member));
        });
    }

    @Test
    void givenMismatchGymId_whenRequireGym_throwsForbiddenException() {
        UserClaims claims = new UserClaims("user-1", "MEMBER", "gym-1", null);
        Context ctx = Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims);
        ctx.run(() -> {
            assertThrows(ForbiddenException.class, () -> GrpcAccessPolicy.requireGym("gym-2"));
            assertThrows(ForbiddenException.class, () -> GrpcAccessPolicy.requireServiceGym("gym-2"));
        });
    }

    @Test
    void givenSuperAdmin_whenRequireGym_returnsWithoutException() {
        UserClaims claims = new UserClaims("admin-1", "SUPER_ADMIN", null, null);
        Context ctx = Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims);
        ctx.run(() -> {
            assertDoesNotThrow(() -> GrpcAccessPolicy.requireGym("gym-2"));
            assertDoesNotThrow(() -> GrpcAccessPolicy.requireGymIds(List.of("gym-1", "gym-2")));
        });
    }

    @Test
    void givenMismatchGymIds_whenRequireGymIds_throwsForbiddenException() {
        UserClaims claims = new UserClaims("user-1", "MEMBER", "gym-1", null);
        Context ctx = Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims);
        ctx.run(() -> {
            assertThrows(ForbiddenException.class, () -> GrpcAccessPolicy.requireGymIds(List.of("gym-1", "gym-2")));
        });
    }
}
