package com.gym.member.application.service;

import com.gym.common.error.NotFoundException;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.model.MembershipStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberJpaRepository memberRepository;
    private final GymLocationJpaRepository gymLocationRepository;

    @Transactional(readOnly = true)
    public MemberDto getMember(String memberId) {
        MemberEntity entity = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found with id: " + memberId));
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public MemberDto getMemberByUserId(String userId) {
        MemberEntity entity = memberRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Member not found for user: " + userId));
        return toDto(entity);
    }

    @Transactional
    public MemberDto createMemberShell(String userId, String fullName, String gymId) {
        if (memberRepository.findByUserId(userId).isPresent()) {
            return toDto(memberRepository.findByUserId(userId).get());
        }

        // If gymId is null or invalid, pick a default active gym location
        String targetGymId = gymId;
        if (targetGymId == null || targetGymId.isBlank()) {
            List<GymLocationEntity> locations = gymLocationRepository.findByStatus("ACTIVE");
            if (!locations.isEmpty()) {
                targetGymId = locations.get(0).getId();
            } else {
                targetGymId = UUID.randomUUID().toString();
            }
        }

        MemberEntity entity = new MemberEntity();
        entity.setUserId(userId);
        entity.setGymId(targetGymId);
        entity.setFullName(fullName != null ? fullName : "Member " + userId.substring(0, 8));
        entity.setStatus(MembershipStatus.NONE);

        MemberEntity saved = memberRepository.save(entity);
        return toDto(saved);
    }

    @Transactional
    public MemberDto updateProfile(String memberId, String fullName, String phone, String avatarUrl, String emergencyContact) {
        MemberEntity entity = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found with id: " + memberId));

        if (fullName != null && !fullName.isBlank()) entity.setFullName(fullName);
        if (phone != null) entity.setPhone(phone);
        if (avatarUrl != null) entity.setAvatarUrl(avatarUrl);
        if (emergencyContact != null) entity.setEmergencyContact(emergencyContact);

        MemberEntity saved = memberRepository.save(entity);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<MemberDto> listMembers(String gymId, int page, int limit) {
        int pageSize = limit > 0 ? Math.min(limit, 100) : 10;
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), pageSize);
        Page<MemberEntity> memberPage;
        if (gymId != null && !gymId.isBlank()) {
            memberPage = memberRepository.findByGymId(gymId, pageRequest);
        } else {
            memberPage = memberRepository.findAll(pageRequest);
        }
        return memberPage.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<MemberDto> listMembersByStatus(MembershipStatus status, List<String> gymIds) {
        List<MemberEntity> entities;
        if (gymIds != null && !gymIds.isEmpty()) {
            entities = memberRepository.findByStatusAndGymIdIn(status, gymIds);
        } else {
            entities = memberRepository.findByStatus(status);
        }
        return entities.stream().map(this::toDto).toList();
    }

    public MemberDto toDto(MemberEntity entity) {
        return new MemberDto(
                UUID.fromString(entity.getId()),
                UUID.fromString(entity.getUserId()),
                UUID.fromString(entity.getGymId()),
                entity.getFullName(),
                entity.getPhone(),
                entity.getAvatarUrl(),
                entity.getEmergencyContact(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
