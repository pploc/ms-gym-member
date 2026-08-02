package com.gym.member.location.adapter.out.persistence.mapper;

import com.gym.member.shared.mapper.CommonMapperUtils;

import com.gym.member.location.adapter.out.persistence.entity.GymQRSecretEntity;
import com.gym.member.location.domain.dto.GymDailySecretDto;
import com.gym.proto.member.v1.GymDailySecretResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;


@Mapper(componentModel = "spring", uses = {CommonMapperUtils.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GymQRSecretMapper {

    @Mapping(target = "gymId", source = "gymId", qualifiedByName = "toUuid")
    GymDailySecretDto toDto(GymQRSecretEntity entity);

    @Mapping(target = "dailySecret", source = "dailySecret")
    GymDailySecretResponse toResponse(GymDailySecretDto dto);
}
