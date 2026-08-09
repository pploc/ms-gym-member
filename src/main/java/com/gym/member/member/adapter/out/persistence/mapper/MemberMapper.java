package com.gym.member.member.adapter.out.persistence.mapper;

import com.gym.member.shared.mapper.CommonMapperUtils;

import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.proto.member.v1.MemberResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;


@Mapper(
        componentModel = "spring",
        uses = {CommonMapperUtils.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "toUuid")
    @Mapping(target = "userId", source = "userId", qualifiedByName = "toUuid")
    MemberDto toDto(MemberEntity entity);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    @Mapping(target = "userId", source = "userId", qualifiedByName = "uuidToString")
    @Mapping(target = "phone", source = "phone", defaultValue = "")
    @Mapping(target = "avatarUrl", source = "avatarUrl", defaultValue = "")
    @Mapping(target = "dateOfBirth", source = "dateOfBirth", qualifiedByName = "dateToString")
    @Mapping(target = "status", expression = "java(dto.status() != null ? dto.status().name() : \"\")")
    MemberResponse toResponse(MemberDto dto);
}
