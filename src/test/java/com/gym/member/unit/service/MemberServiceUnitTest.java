package com.gym.member.unit.service;

import com.gym.common.error.NotFoundException;
import com.gym.common.pagination.NormalPage;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.mapper.MemberMapper;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.application.service.MemberService;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceUnitTest {

    @Mock
    private MemberJpaRepository memberRepository;

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
    void given_existing_member_id_when_get_member_then_returns_member_dto() {
        // given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        // when
        MemberDto dto = memberService.getMember(memberId);

        // then
        assertNotNull(dto);
        assertEquals(memberId, dto.id().toString());
        assertEquals("John Doe", dto.fullName());
    }

    @Test
    void given_missing_member_id_when_get_member_then_throws_not_found_exception() {
        // given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // when / then
        assertThrows(NotFoundException.class, () -> memberService.getMember(memberId));
    }

    @Test
    void given_existing_user_id_when_get_member_by_user_id_then_returns_member_dto() {
        // given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));

        // when
        MemberDto dto = memberService.getMemberByUserId(userId);

        // then
        assertNotNull(dto);
        assertEquals(userId, dto.userId().toString());
    }

    @Test
    void given_missing_user_id_when_get_member_by_user_id_then_throws_not_found_exception() {
        // given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // when / then
        assertThrows(NotFoundException.class, () -> memberService.getMemberByUserId(userId));
    }

    @Test
    void given_existing_member_shell_when_create_member_shell_then_returns_existing_member_without_saving() {
        // given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(member));

        // when
        MemberDto dto = memberService.createMemberShell(userId, "John Doe");

        // then
        assertNotNull(dto);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void given_new_user_when_create_member_shell_then_saves_and_returns_new_member_dto() {
        // given
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(memberRepository.save(any())).thenAnswer(inv -> {
            MemberEntity entity = inv.getArgument(0);
            entity.setId(memberId);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        // when
        MemberDto dto = memberService.createMemberShell(userId, "John Doe");

        // then
        assertNotNull(dto);
        assertEquals("John Doe", dto.fullName());
    }

    @Test
    void given_null_or_blank_full_name_when_create_member_shell_then_throws_illegal_argument_exception() {
        // given / when / then
        assertThrows(IllegalArgumentException.class, () -> memberService.createMemberShell(userId, null));
        assertThrows(IllegalArgumentException.class, () -> memberService.createMemberShell(userId, "   "));
    }

    @Test
    void given_existing_member_when_update_profile_then_updates_fields_and_returns_dto() {
        // given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LocalDate dob = LocalDate.of(1990, 1, 1);

        // when
        MemberDto dto = memberService.updateProfile(memberId, "Jane Doe", "123456789", "http://avatar", dob);

        // then
        assertNotNull(dto);
        assertEquals("Jane Doe", dto.fullName());
        assertEquals("123456789", dto.phone());
        assertEquals("http://avatar", dto.avatarUrl());
        assertEquals(dob, dto.dateOfBirth());
    }

    @Test
    void given_blank_full_name_and_null_fields_when_update_profile_then_preserves_existing_fields() {
        // given
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        MemberDto dto = memberService.updateProfile(memberId, "   ", null, null, null);

        // then
        assertNotNull(dto);
        assertEquals("John Doe", dto.fullName());
    }

    @Test
    void given_missing_member_when_update_profile_then_throws_not_found_exception() {
        // given
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // when / then
        assertThrows(NotFoundException.class, () -> memberService.updateProfile(memberId, "Jane", null, null, null));
    }

    @Test
    void given_gym_id_filter_when_list_members_then_uses_specification_and_returns_paged_members() {
        // given
        Page<MemberEntity> page = new PageImpl<>(List.of(member));
        when(memberRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // when
        NormalPage<MemberDto> result = memberService.listMembers(gymId, 0, 10);

        // then
        assertNotNull(result);
        assertEquals(1, result.totalRecords());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void given_no_gym_id_filter_when_list_members_then_still_uses_specification_path() {
        // given
        Page<MemberEntity> page = new PageImpl<>(List.of(member));
        when(memberRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // when
        NormalPage<MemberDto> result = memberService.listMembers(null, 0, 10);

        // then
        assertNotNull(result);
        assertEquals(1, result.totalRecords());
        verify(memberRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void given_invalid_page_and_limit_when_list_members_then_uses_default_page_size_and_page_zero() {
        // given
        Page<MemberEntity> page = new PageImpl<>(List.of(member));
        when(memberRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // when
        NormalPage<MemberDto> result = memberService.listMembers("   ", -1, 0);

        // then
        assertNotNull(result);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void given_status_and_gym_ids_when_list_members_by_status_then_uses_specification() {
        // given
        PageRequest expected = PageRequest.of(0, 1000);
        when(memberRepository.findAll(any(Specification.class), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(member), expected, 1));

        // when
        List<MemberDto> result = memberService.listMembersByStatus(MembershipStatus.ACTIVE, List.of(gymId));

        // then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(memberRepository).findAll(any(Specification.class), eq(expected));
        verify(memberRepository, never()).findByStatus(any(), any());
    }

    @Test
    void given_status_without_gym_ids_when_list_members_by_status_then_uses_status_specification() {
        // given
        PageRequest expected = PageRequest.of(0, 1000);
        when(memberRepository.findAll(any(Specification.class), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(member), expected, 1));

        // when
        List<MemberDto> result = memberService.listMembersByStatus(MembershipStatus.ACTIVE, null);

        // then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void given_empty_gym_ids_list_when_list_members_by_status_then_uses_status_specification() {
        // given
        PageRequest expected = PageRequest.of(0, 1000);
        when(memberRepository.findAll(any(Specification.class), eq(expected)))
                .thenReturn(new PageImpl<>(List.of(member), expected, 1));

        // when
        List<MemberDto> result =
                memberService.listMembersByStatus(MembershipStatus.ACTIVE, Collections.emptyList());

        // then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void given_result_hits_safety_limit_when_list_members_by_status_then_still_maps_all_returned_rows() {
        // given
        List<MemberEntity> batch = java.util.stream.Stream.generate(() -> {
                    MemberEntity e = new MemberEntity();
                    e.setId(UUID.randomUUID().toString());
                    e.setUserId(UUID.randomUUID().toString());
                    e.setStatus(MembershipStatus.ACTIVE);
                    return e;
                })
                .limit(1000)
                .toList();
        PageRequest expected = PageRequest.of(0, 1000);
        when(memberRepository.findAll(any(Specification.class), eq(expected)))
                .thenReturn(new PageImpl<>(batch, expected, 1000));

        // when
        List<MemberDto> result = memberService.listMembersByStatus(MembershipStatus.ACTIVE, null);

        // then
        assertEquals(1000, result.size());
    }
}
