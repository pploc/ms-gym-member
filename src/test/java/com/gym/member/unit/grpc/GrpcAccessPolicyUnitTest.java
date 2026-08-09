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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcAccessPolicyUnitTest {

    @Test
    void given_mismatch_user_id_when_require_self_then_throws_forbidden_exception() {
        // given
        UserClaims claims = new UserClaims("user-1", "CUSTOMER", "gym-1", null);
        Context ctx = Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims);

        // when / then
        ctx.run(() -> {
            MemberDto member = new MemberDto(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Name",
                    null,
                    null,
                    null,
                    MembershipStatus.ACTIVE,
                    Instant.now(),
                    Instant.now());
            assertThrows(ForbiddenException.class, () -> GrpcAccessPolicy.requireSelf(member));
        });
    }

    @Test
    void given_mismatch_gym_id_when_require_gym_then_throws_forbidden_exception() {
        // given
        UserClaims claims = new UserClaims("user-1", "ADMIN", "gym-1", null);
        Context ctx = Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims);

        // when / then
        ctx.run(() -> assertThrows(ForbiddenException.class, () -> GrpcAccessPolicy.requireGym("gym-2")));
    }

    @Test
    void given_blank_gym_id_when_require_gym_for_admin_then_throws_forbidden_exception() {
        // given
        UserClaims claims = new UserClaims("user-1", "ADMIN", "gym-1", null);
        Context ctx = Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims);

        // when / then
        ctx.run(() -> assertThrows(ForbiddenException.class, () -> GrpcAccessPolicy.requireGym("")));
    }

    @Test
    void given_super_admin_when_require_gym_then_returns_without_exception() {
        // given
        UserClaims claims = new UserClaims("admin-1", "SUPER_ADMIN", null, null);
        Context ctx = Context.current().withValue(GrpcSecurityContext.CLAIMS_KEY, claims);

        // when / then
        ctx.run(() -> {
            assertDoesNotThrow(() -> GrpcAccessPolicy.requireGym("gym-2"));
            assertDoesNotThrow(() -> GrpcAccessPolicy.requireGym(null));
        });
    }
}
