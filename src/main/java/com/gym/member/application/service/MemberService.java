package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.common.pagination.NormalPage;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

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
        entity.setFullName(fullName != null && !fullName.isBlank() ? fullName : "Member " + userId.substring(0, Math.min(8, userId.length())));
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

    @Transactional(readOnly = true)
    public List<MemberDto> listMembersByStatus(MembershipStatus status, List<String> gymIds) {
        List<MemberEntity> entities;
        if (gymIds != null && !gymIds.isEmpty()) {
            entities = memberRepository.findByStatusAndGymIdIn(status, gymIds);
        } else {
            entities = memberRepository.findByStatus(status);
        }
        return entities.stream().map(memberMapper::toDto).toList();
    }
}
