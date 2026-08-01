package com.gym.member.mapper;

import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.domain.dto.MemberDto;
import com.gym.proto.member.v1.MemberResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "toUuid")
    @Mapping(target = "userId", source = "userId", qualifiedByName = "toUuid")
    @Mapping(target = "gymId", source = "gymId", qualifiedByName = "toUuid")
    MemberDto toDto(MemberEntity entity);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    @Mapping(target = "userId", source = "userId", qualifiedByName = "uuidToString")
    @Mapping(target = "gymId", source = "gymId", qualifiedByName = "uuidToString")
    @Mapping(target = "phone", source = "phone", defaultValue = "")
    @Mapping(target = "avatarUrl", source = "avatarUrl", defaultValue = "")
    @Mapping(target = "emergencyContact", source = "emergencyContact", defaultValue = "")
    @Mapping(target = "status", expression = "java(dto.status() != null ? dto.status().name() : \"\")")
    MemberResponse toResponse(MemberDto dto);

    @Named("toUuid")
    default UUID toUuid(String str) {
        return str != null ? UUID.fromString(str) : null;
    }

    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : "";
    }
}
