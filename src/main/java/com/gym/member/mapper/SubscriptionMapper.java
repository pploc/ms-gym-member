package com.gym.member.mapper;

import com.gym.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.proto.member.v1.MembershipResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDate;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {CommonMapperUtils.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubscriptionMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "toUuid")
    @Mapping(target = "memberId", source = "memberId", qualifiedByName = "toUuid")
    @Mapping(target = "planId", source = "planId", qualifiedByName = "toUuid")
    SubscriptionDto toDto(SubscriptionEntity entity);

    @Mapping(target = "memberId", source = "memberId", qualifiedByName = "uuidToString")
    @Mapping(target = "status", expression = "java(dto.status() != null ? dto.status().name() : \"\")")
    @Mapping(target = "startDate", source = "startDate", qualifiedByName = "dateToString")
    @Mapping(target = "endDate", source = "endDate", qualifiedByName = "dateToString")
    @Mapping(target = "remainingDays", source = "remainingDays", defaultValue = "0")
    MembershipResponse toResponse(SubscriptionDto dto);
}
