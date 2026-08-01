package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.model.MembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberJpaRepository memberRepository;

    @Mock
    private GymLocationJpaRepository gymLocationRepository;

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
        member.setGymId(gymId);
        member.setFullName("John Doe");
        member.setStatus(MembershipStatus.ACTIVE);
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
    }

    @Test
    void getMember_success() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        MemberDto dto = memberService.getMember(memberId);

        assertNotNull(dto);
        assertEquals(memberId, dto.id().toString());
        assertEquals("John Doe", dto.fullName());
    }

    @Test
    void getMember_notFound() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> memberService.getMember(memberId));
    }

    @Test
    void getMemberByUserId_success() {
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));

        MemberDto dto = memberService.getMemberByUserId(userId);

        assertNotNull(dto);
        assertEquals(userId, dto.userId().toString());
    }

    @Test
    void getMemberByUserId_notFound() {
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> memberService.getMemberByUserId(userId));
    }

    @Test
    void createMemberShell_existingMember() {
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));

        MemberDto dto = memberService.createMemberShell(userId, "John Doe", gymId);

        assertNotNull(dto);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void createMemberShell_newMember_withGymId() {
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(memberRepository.save(any())).thenAnswer(inv -> {
            MemberEntity entity = inv.getArgument(0);
            entity.setId(memberId);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        MemberDto dto = memberService.createMemberShell(userId, "John Doe", gymId);

        assertNotNull(dto);
        assertEquals("John Doe", dto.fullName());
        assertEquals(gymId, dto.gymId().toString());
    }

    @Test
    void createMemberShell_newMember_nullGymId_foundActiveLocation() {
        GymLocationEntity location = new GymLocationEntity();
        location.setId(gymId);
        location.setStatus("ACTIVE");

        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(gymLocationRepository.findByStatus("ACTIVE")).thenReturn(List.of(location));
        when(memberRepository.save(any())).thenAnswer(inv -> {
            MemberEntity entity = inv.getArgument(0);
            entity.setId(memberId);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        MemberDto dto = memberService.createMemberShell(userId, null, null);

        assertNotNull(dto);
        assertTrue(dto.fullName().startsWith("Member "));
        assertEquals(gymId, dto.gymId().toString());
    }

    @Test
    void createMemberShell_newMember_nullGymId_noActiveLocation() {
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(gymLocationRepository.findByStatus("ACTIVE")).thenReturn(List.of());
        when(memberRepository.save(any())).thenAnswer(inv -> {
            MemberEntity entity = inv.getArgument(0);
            entity.setId(memberId);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        MemberDto dto = memberService.createMemberShell(userId, null, "");

        assertNotNull(dto);
        assertNotNull(dto.gymId());
    }

    @Test
    void updateProfile_success() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MemberDto dto = memberService.updateProfile(memberId, "Jane Doe", "123456789", "http://avatar", "987654321");

        assertNotNull(dto);
        assertEquals("Jane Doe", dto.fullName());
        assertEquals("123456789", dto.phone());
        assertEquals("http://avatar", dto.avatarUrl());
        assertEquals("987654321", dto.emergencyContact());
    }

    @Test
    void updateProfile_notFound() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> memberService.updateProfile(memberId, "Jane", null, null, null));
    }

    @Test
    void listMembers_withGymId() {
        Page<MemberEntity> page = new PageImpl<>(List.of(member));
        when(memberRepository.findByGymId(eq(gymId), any(PageRequest.class))).thenReturn(page);

        Page<MemberDto> result = memberService.listMembers(gymId, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void listMembers_withoutGymId() {
        Page<MemberEntity> page = new PageImpl<>(List.of(member));
        when(memberRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<MemberDto> result = memberService.listMembers(null, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void listMembersByStatus_withGymIds() {
        when(memberRepository.findByStatusAndGymIdIn(MembershipStatus.ACTIVE, List.of(gymId))).thenReturn(List.of(member));

        List<MemberDto> result = memberService.listMembersByStatus(MembershipStatus.ACTIVE, List.of(gymId));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void listMembersByStatus_withoutGymIds() {
        when(memberRepository.findByStatus(MembershipStatus.ACTIVE)).thenReturn(List.of(member));

        List<MemberDto> result = memberService.listMembersByStatus(MembershipStatus.ACTIVE, null);

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}
