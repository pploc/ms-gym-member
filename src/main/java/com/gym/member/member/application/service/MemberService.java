package com.gym.member.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.common.pagination.NormalPage;
import com.gym.member.location.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.location.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.adapter.out.persistence.mapper.MemberMapper;
import com.gym.member.member.application.port.in.MemberUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService implements MemberUseCase {

    private final MemberJpaRepository memberRepository;
    private final GymLocationJpaRepository gymLocationRepository;
    private final MemberMapper memberMapper;

    @Transactional(readOnly = true)
    public MemberDto getMember(String memberId) {
        MemberEntity entity = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found with id: " + memberId));
        return memberMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public MemberDto getMemberByUserId(String userId) {
        MemberEntity entity = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Member not found for user: " + userId));
        return memberMapper.toDto(entity);
    }

    @Transactional
    public MemberDto createMemberShell(String userId, String fullName, String gymId) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName is required and cannot be blank");
        }
        if (gymId == null || gymId.isBlank()) {
            throw new IllegalArgumentException("gymId is required and cannot be blank");
        }

        GymLocationEntity gymLocation = gymLocationRepository.findById(gymId)
                .orElseThrow(() -> new NotFoundException("Gym location not found with id: " + gymId));

        if (memberRepository.findByUserId(userId).isPresent()) {
            return memberMapper.toDto(memberRepository.findByUserId(userId).get());
        }

        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setGymId(gymLocation.getId());
        entity.setFullName(fullName);
        entity.setStatus(MembershipStatus.NONE);

        MemberEntity saved = memberRepository.save(entity);
        return memberMapper.toDto(saved);
    }

    @Transactional
    public MemberDto updateProfile(String memberId, String fullName, String phone, String avatarUrl, LocalDate dateOfBirth) {
        MemberEntity entity = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found with id: " + memberId));

        if (fullName != null && !fullName.isBlank()) entity.setFullName(fullName);
        if (phone != null) entity.setPhone(phone);
        if (avatarUrl != null) entity.setAvatarUrl(avatarUrl);
        if (dateOfBirth != null) entity.setDateOfBirth(dateOfBirth);

        MemberEntity saved = memberRepository.save(entity);
        return memberMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public NormalPage<MemberDto> listMembers(String gymId, int page, int limit) {
        int pageSize = limit > 0 ? Math.min(limit, 100) : 10;
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), pageSize);
        Page<MemberEntity> memberPage;
        if (gymId != null && !gymId.isBlank()) {
            memberPage = memberRepository.findByGymId(gymId, pageRequest);
        } else {
            memberPage = memberRepository.findAll(pageRequest);
        }
        return new NormalPage<>(
                memberPage.getContent().stream().map(memberMapper::toDto).toList(),
                memberPage.getNumber(),
                memberPage.getSize(),
                memberPage.getTotalElements(),
                memberPage.getTotalPages()
        );
    }

    private static final int MAX_UNBOUNDED_RESULT_LIMIT = 1000;

    @Transactional(readOnly = true)
    public List<MemberDto> listMembersByStatus(MembershipStatus status, List<String> gymIds) {
        PageRequest safetyLimit = PageRequest.of(0, MAX_UNBOUNDED_RESULT_LIMIT);
        List<MemberEntity> entities;
        if (gymIds != null && !gymIds.isEmpty()) {
            entities = memberRepository.findByStatusAndGymIdIn(status, gymIds, safetyLimit);
        } else {
            entities = memberRepository.findByStatus(status, safetyLimit);
        }
        if (entities.size() >= MAX_UNBOUNDED_RESULT_LIMIT) {
            log.warn("listMembersByStatus reached safety limit threshold of {}. Query results may be truncated for status={}", MAX_UNBOUNDED_RESULT_LIMIT, status);
        }
        return entities.stream().map(memberMapper::toDto).toList();
    }
}
