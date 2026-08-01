package com.gym.member.mapper;

import com.gym.member.adapter.out.persistence.entity.GymQRSecretEntity;
import com.gym.member.domain.dto.GymDailySecretDto;
import com.gym.proto.member.v1.GymDailySecretResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GymQRSecretMapper {

    @Mapping(target = "gymId", source = "gymId", qualifiedByName = "toUuid")
    GymDailySecretDto toDto(GymQRSecretEntity entity);

    @Mapping(target = "dailySecret", source = "dailySecret")
    GymDailySecretResponse toResponse(GymDailySecretDto dto);

    @Named("toUuid")
    default UUID toUuid(String str) {
        return str != null ? UUID.fromString(str) : null;
    }
}
