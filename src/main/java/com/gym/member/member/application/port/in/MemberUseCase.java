package com.gym.member.member.application.port.in;

import com.gym.common.pagination.NormalPage;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;

import java.time.LocalDate;
import java.util.List;

public interface MemberUseCase {
    MemberDto getMember(String memberId);
    MemberDto getMemberByUserId(String userId);
    MemberDto createMemberShell(String userId, String fullName, String gymId);
    MemberDto updateProfile(String memberId, String fullName, String phone, String avatarUrl, LocalDate dateOfBirth);
    NormalPage<MemberDto> listMembers(String gymId, int page, int limit);
    List<MemberDto> listMembersByStatus(MembershipStatus status, List<String> gymIds);
}
