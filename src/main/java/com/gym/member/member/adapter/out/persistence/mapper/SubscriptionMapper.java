package com.gym.member.member.adapter.out.persistence.mapper;

import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.member.shared.mapper.CommonMapperUtils;
import com.gym.member.shared.mapper.ProtoEnums;
import com.gym.proto.member.v1.GetMembershipStatusByUserIdResponse;
import com.gym.proto.member.v1.GetMembershipStatusResponse;
import com.gym.proto.member.v1.PauseMembershipResponse;
import com.gym.proto.member.v1.ResumeMembershipResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = {CommonMapperUtils.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubscriptionMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "toUuid")
    @Mapping(target = "memberId", source = "memberId", qualifiedByName = "toUuid")
    @Mapping(target = "gymId", source = "gymId", qualifiedByName = "toUuid")
    @Mapping(target = "planId", source = "planId", qualifiedByName = "toUuid")
    SubscriptionDto toDto(SubscriptionEntity entity);

    default PauseMembershipResponse toPauseResponse(SubscriptionDto dto) {
        return PauseMembershipResponse.newBuilder()
                .setMemberId(memberId(dto))
                .setStatus(ProtoEnums.toProto(dto.status()))
                .setStartDate(date(dto.startDate()))
                .setEndDate(date(dto.endDate()))
                .setRemainingDays(dto.remainingDays() == null ? 0 : dto.remainingDays())
                .build();
    }

    default ResumeMembershipResponse toResumeResponse(SubscriptionDto dto) {
        return ResumeMembershipResponse.newBuilder()
                .setMemberId(memberId(dto))
                .setStatus(ProtoEnums.toProto(dto.status()))
                .setStartDate(date(dto.startDate()))
                .setEndDate(date(dto.endDate()))
                .setRemainingDays(dto.remainingDays() == null ? 0 : dto.remainingDays())
                .build();
    }

    default GetMembershipStatusResponse toStatusResponse(SubscriptionDto dto) {
        return GetMembershipStatusResponse.newBuilder()
                .setMemberId(memberId(dto))
                .setStatus(ProtoEnums.toProto(dto.status()))
                .setStartDate(date(dto.startDate()))
                .setEndDate(date(dto.endDate()))
                .setRemainingDays(dto.remainingDays() == null ? 0 : dto.remainingDays())
                .build();
    }

    default GetMembershipStatusByUserIdResponse toStatusByUserIdResponse(SubscriptionDto dto) {
        return GetMembershipStatusByUserIdResponse.newBuilder()
                .setMemberId(memberId(dto))
                .setStatus(ProtoEnums.toProto(dto.status()))
                .setStartDate(date(dto.startDate()))
                .setEndDate(date(dto.endDate()))
                .setRemainingDays(dto.remainingDays() == null ? 0 : dto.remainingDays())
                .build();
    }

    private static String memberId(SubscriptionDto dto) {
        return dto.memberId() == null ? "" : dto.memberId().toString();
    }

    private static String date(java.time.LocalDate value) {
        return value == null ? "" : value.toString();
    }
}
