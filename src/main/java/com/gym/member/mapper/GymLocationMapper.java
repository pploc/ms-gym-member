package com.gym.member.mapper;

import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.MembershipPlanEntity;
import com.gym.member.domain.dto.GymLocationDto;
import com.gym.member.domain.dto.PlanDto;
import com.gym.proto.member.v1.GymLocationResponse;
import com.gym.proto.member.v1.Plan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GymLocationMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "toUuid")
    @Mapping(target = "chainId", source = "chainId", qualifiedByName = "toUuid")
    GymLocationDto toDto(GymLocationEntity entity);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    @Mapping(target = "chainId", source = "chainId", qualifiedByName = "uuidToString")
    GymLocationResponse toResponse(GymLocationDto dto);

    @Mapping(target = "id", source = "id", qualifiedByName = "toUuid")
    @Mapping(target = "gymId", source = "gymId", qualifiedByName = "toUuid")
    PlanDto toPlanDto(MembershipPlanEntity entity);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    @Mapping(target = "planType", expression = "java(dto.planType() != null ? dto.planType().name() : \"\")")
    @Mapping(target = "durationDays", source = "durationDays", defaultValue = "0")
    @Mapping(target = "description", source = "description", defaultValue = "")
    Plan toPlanProto(PlanDto dto);

    @Named("toUuid")
    default UUID toUuid(String str) {
        return str != null ? UUID.fromString(str) : null;
    }

    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : "";
    }
}
