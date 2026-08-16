package com.gym.member.member.domain.dto;

import com.gym.member.member.domain.model.MembershipStatus;

public record MembershipValidation(String memberId, MembershipStatus status) {}
