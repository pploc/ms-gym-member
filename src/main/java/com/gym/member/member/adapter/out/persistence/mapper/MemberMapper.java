package com.gym.member.member.adapter.out.persistence.mapper;

import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.shared.mapper.CommonMapperUtils;
import com.gym.member.shared.mapper.ProtoEnums;
import com.gym.proto.member.v1.GetMemberResponse;
import com.gym.proto.member.v1.UpdateProfileResponse;
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

    default GetMemberResponse toGetMemberResponse(MemberDto dto) {
        return GetMemberResponse.newBuilder()
                .setId(str(dto.id()))
                .setUserId(str(dto.userId()))
                .setFullName(nullToEmpty(dto.fullName()))
                .setPhone(nullToEmpty(dto.phone()))
                .setAvatarUrl(nullToEmpty(dto.avatarUrl()))
                .setDateOfBirth(dto.dateOfBirth() != null ? dto.dateOfBirth().toString() : "")
                .setStatus(ProtoEnums.toProto(dto.status()))
                .build();
    }

    default UpdateProfileResponse toUpdateProfileResponse(MemberDto dto) {
        return UpdateProfileResponse.newBuilder()
                .setId(str(dto.id()))
                .setUserId(str(dto.userId()))
                .setFullName(nullToEmpty(dto.fullName()))
                .setPhone(nullToEmpty(dto.phone()))
                .setAvatarUrl(nullToEmpty(dto.avatarUrl()))
                .setDateOfBirth(dto.dateOfBirth() != null ? dto.dateOfBirth().toString() : "")
                .setStatus(ProtoEnums.toProto(dto.status()))
                .build();
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
