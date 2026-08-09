package com.gym.member.member.domain.constant;

public final class MemberEventTopics {

    private MemberEventTopics() {}

    public static final String AGGREGATE_TYPE_MEMBER = "member";

    public static final String MEMBERSHIP_ACTIVATED = "membership.activated.v1";
    public static final String MEMBERSHIP_PAUSED = "membership.paused.v1";
    public static final String MEMBERSHIP_RESUMED = "membership.resumed.v1";
    public static final String MEMBERSHIP_EXPIRED = "membership.expired.v1";
    public static final String MEMBERSHIP_EXPIRING_SOON = "membership.expiring-soon.v1";
}
