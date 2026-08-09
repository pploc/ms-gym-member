package com.gym.member.shared.mapper;

import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;

public final class ProtoEnums {

    private ProtoEnums() {}

    public static com.gym.proto.common.v1.MembershipStatus toProto(MembershipStatus status) {
        if (status == null) {
            return com.gym.proto.common.v1.MembershipStatus.MEMBERSHIP_STATUS_UNSPECIFIED;
        }
        return switch (status) {
            case NONE -> com.gym.proto.common.v1.MembershipStatus.MEMBERSHIP_STATUS_NONE;
            case ACTIVE -> com.gym.proto.common.v1.MembershipStatus.MEMBERSHIP_STATUS_ACTIVE;
            case PAUSED -> com.gym.proto.common.v1.MembershipStatus.MEMBERSHIP_STATUS_PAUSED;
            case EXPIRED -> com.gym.proto.common.v1.MembershipStatus.MEMBERSHIP_STATUS_EXPIRED;
        };
    }

    public static MembershipStatus toDomain(com.gym.proto.common.v1.MembershipStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        return switch (status) {
            case MEMBERSHIP_STATUS_NONE -> MembershipStatus.NONE;
            case MEMBERSHIP_STATUS_ACTIVE -> MembershipStatus.ACTIVE;
            case MEMBERSHIP_STATUS_PAUSED -> MembershipStatus.PAUSED;
            case MEMBERSHIP_STATUS_EXPIRED -> MembershipStatus.EXPIRED;
            case MEMBERSHIP_STATUS_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("status must be NONE, ACTIVE, PAUSED, or EXPIRED");
        };
    }

    public static com.gym.proto.common.v1.PlanType toProto(PlanType planType) {
        if (planType == null) {
            return com.gym.proto.common.v1.PlanType.PLAN_TYPE_UNSPECIFIED;
        }
        return switch (planType) {
            case MONTHLY -> com.gym.proto.common.v1.PlanType.PLAN_TYPE_MONTHLY;
            case YEARLY -> com.gym.proto.common.v1.PlanType.PLAN_TYPE_YEARLY;
            case LIFETIME -> com.gym.proto.common.v1.PlanType.PLAN_TYPE_LIFETIME;
        };
    }

    public static PlanType toDomain(com.gym.proto.common.v1.PlanType planType) {
        if (planType == null) {
            throw new IllegalArgumentException("plan_type is required");
        }
        return switch (planType) {
            case PLAN_TYPE_MONTHLY -> PlanType.MONTHLY;
            case PLAN_TYPE_YEARLY -> PlanType.YEARLY;
            case PLAN_TYPE_LIFETIME -> PlanType.LIFETIME;
            case PLAN_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("plan_type must be MONTHLY, YEARLY, or LIFETIME");
        };
    }
}
