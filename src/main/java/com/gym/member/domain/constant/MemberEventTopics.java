package com.gym.member.domain.constant;

public final class MemberEventTopics {

    private MemberEventTopics() {}

    public static final String AGGREGATE_TYPE_MEMBER = "member";

    public static final String MEMBERSHIP_ACTIVATED = "membership.activated";
    public static final String MEMBERSHIP_PAUSED = "membership.paused";
    public static final String MEMBERSHIP_RESUMED = "membership.resumed";
    public static final String MEMBERSHIP_EXPIRED = "membership.expired";
    public static final String MEMBERSHIP_EXPIRING_SOON = "membership.expiring-soon";
}
