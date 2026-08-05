package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.common.pagination.NormalPage;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.location.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.application.service.MemberService;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.adapter.out.persistence.mapper.MemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceUnitTest {

    @Mock
    private MemberJpaRepository memberRepository;

    @Mock
    private GymLocationJpaRepository gymLocationRepository;

    @Spy
    private MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @InjectMocks
    private MemberService memberService;

    private String memberId;
    private String userId;
    private String gymId;
    private MemberEntity member;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        gymId = UUID.randomUUID().toString();

        member = new MemberEntity();
        member.setId(memberId);
        member.setUserId(userId);
        member.setFullName("John Doe");
        member.setStatus(MembershipStatus.ACTIVE);
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
    }

    @Test
    void givenExistingMemberId_whenGetMember_thenReturnsMemberDto() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        // When
        MemberDto dto = memberService.getMember(memberId);

        // Then
        assertNotNull(dto);
        assertEquals(memberId, dto.id().toString());
        assertEquals("John Doe", dto.fullName());
    }

    @Test
    void givenMissingMemberId_whenGetMember_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> memberService.getMember(memberId));
    }

    @Test
    void givenExistingUserId_whenGetMemberByUserId_thenReturnsMemberDto() {
        // Given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));

        // When
        MemberDto dto = memberService.getMemberByUserId(userId);

        // Then
        assertNotNull(dto);
        assertEquals(userId, dto.userId().toString());
    }

    @Test
    void givenMissingUserId_whenGetMemberByUserId_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> memberService.getMemberByUserId(userId));
    }

    @Test
    void givenExistingMemberShell_whenCreateMemberShell_thenReturnsExistingMemberWithoutSaving() {
        // Given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));

        // When
        MemberDto dto = memberService.createMemberShell(userId, "John Doe");

        // Then
        assertNotNull(dto);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void givenNewUser_whenCreateMemberShell_thenSavesAndReturnsNewMemberDto() {
        // Given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(memberRepository.save(any())).thenAnswer(inv -> {
            MemberEntity entity = inv.getArgument(0);
            entity.setId(memberId);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        // When
        MemberDto dto = memberService.createMemberShell(userId, "John Doe");

        // Then
        assertNotNull(dto);
        assertEquals("John Doe", dto.fullName());
    }

    @Test
    void givenNullOrBlankFullName_whenCreateMemberShell_thenThrowsIllegalArgumentException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> memberService.createMemberShell(userId, null));
        assertThrows(IllegalArgumentException.class, () -> memberService.createMemberShell(userId, "   "));
    }

    @Test
    void givenExistingMember_whenUpdateProfile_thenUpdatesFieldsAndReturnsDto() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        LocalDate dob = LocalDate.of(1990, 1, 1);
        MemberDto dto = memberService.updateProfile(memberId, "Jane Doe", "123456789", "http://avatar", dob);

        // Then
        assertNotNull(dto);
        assertEquals("Jane Doe", dto.fullName());
        assertEquals("123456789", dto.phone());
        assertEquals("http://avatar", dto.avatarUrl());
        assertEquals(dob, dto.dateOfBirth());
    }

    @Test
    void givenBlankFullNameAndNullFields_whenUpdateProfile_thenPreservesExistingFields() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        MemberDto dto = memberService.updateProfile(memberId, "   ", null, null, null);

        // Then
        assertNotNull(dto);
        assertEquals("John Doe", dto.fullName());
    }

    @Test
    void givenMissingMember_whenUpdateProfile_thenThrowsNotFoundException() {
        // Given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> memberService.updateProfile(memberId, "Jane", null, null, null));
    }

    @Test
    void givenGymIdFilter_whenListMembers_thenReturnsPagedMembers() {
        // Given
        Page<MemberEntity> page = new PageImpl<>(List.of(member));
        when(memberRepository.findAll(any(PageRequest.class))).thenReturn(page);

        // When
        NormalPage<MemberDto> result = memberService.listMembers(gymId, 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.totalRecords());
    }

    @Test
    void givenNoGymIdFilter_whenListMembers_thenReturnsAllPagedMembers() {
        // Given
        Page<MemberEntity> page = new PageImpl<>(List.of(member));
        when(memberRepository.findAll(any(PageRequest.class))).thenReturn(page);

        // When
        NormalPage<MemberDto> result = memberService.listMembers(null, 0, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.totalRecords());
    }

    @Test
    void givenInvalidPageAndLimit_whenListMembers_thenUsesDefaultPageSizeAndPageZero() {
        // Given
        Page<MemberEntity> page = new PageImpl<>(List.of(member));
        when(memberRepository.findAll(any(PageRequest.class))).thenReturn(page);

        // When
        NormalPage<MemberDto> result = memberService.listMembers("   ", -1, 0);

        // Then
        assertNotNull(result);
        assertEquals(1, result.totalRecords());
    }

    @Test
    void givenStatusAndGymIds_whenListMembersByStatus_thenReturnsFilteredMembers() {
        // Given
        when(memberRepository.findByStatus(eq(MembershipStatus.ACTIVE), any())).thenReturn(List.of(member));

        // When
        List<MemberDto> result = memberService.listMembersByStatus(MembershipStatus.ACTIVE, List.of(gymId));

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void givenStatusWithoutGymIds_whenListMembersByStatus_thenReturnsAllMembersWithStatus() {
        // Given
        when(memberRepository.findByStatus(eq(MembershipStatus.ACTIVE), any())).thenReturn(List.of(member));

        // When
        List<MemberDto> result = memberService.listMembersByStatus(MembershipStatus.ACTIVE, null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void givenEmptyGymIdsList_whenListMembersByStatus_thenReturnsAllMembersWithStatus() {
        // Given
        when(memberRepository.findByStatus(eq(MembershipStatus.ACTIVE), any())).thenReturn(List.of(member));

        // When
        List<MemberDto> result = memberService.listMembersByStatus(MembershipStatus.ACTIVE, Collections.emptyList());

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}
