package com.gym.member.member.adapter.in.grpc;

import com.gym.common.pagination.NormalPage;
import com.gym.member.member.adapter.out.persistence.mapper.MemberMapper;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.dto.MembershipValidation;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.shared.mapper.ProtoEnums;
import com.gym.proto.member.v1.GetMemberRequest;
import com.gym.proto.member.v1.GetMemberResponse;
import com.gym.proto.member.v1.ListMembersByStatusRequest;
import com.gym.proto.member.v1.ListMembersByStatusResponse;
import com.gym.proto.member.v1.ListMembersRequest;
import com.gym.proto.member.v1.ListMembersResponse;
import com.gym.proto.member.v1.UpdateProfileRequest;
import com.gym.proto.member.v1.UpdateProfileResponse;
import com.gym.proto.member.v1.ValidateMembershipRequest;
import com.gym.proto.member.v1.ValidateMembershipResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireSelf;
import static com.gym.member.adapter.in.grpc.GrpcErrorHandler.execute;

@Component
@RequiredArgsConstructor
public class MemberGrpcDelegate {

    private final MemberUseCase memberUseCase;
    private final SubscriptionLifecycleUseCase subscriptionLifecycleUseCase;
    private final MemberMapper memberMapper;

    public void getMember(GetMemberRequest request, StreamObserver<GetMemberResponse> responseObserver) {
        execute(responseObserver, () -> {
            MemberDto dto = memberUseCase.getMember(request.getMemberId());
            requireSelf(dto);
            return memberMapper.toGetMemberResponse(dto);
        });
    }

    public void updateProfile(UpdateProfileRequest request, StreamObserver<UpdateProfileResponse> responseObserver) {
        execute(responseObserver, () -> {
            MemberDto current = memberUseCase.getMember(request.getMemberId());
            requireSelf(current);
            LocalDate dateOfBirth = (request.getDateOfBirth() != null && !request.getDateOfBirth().isBlank())
                    ? LocalDate.parse(request.getDateOfBirth())
                    : null;
            MemberDto dto = memberUseCase.updateProfile(
                    request.getMemberId(),
                    request.getFullName(),
                    request.getPhone(),
                    request.getAvatarUrl(),
                    dateOfBirth
            );
            return memberMapper.toUpdateProfileResponse(dto);
        });
    }

    public void listMembers(ListMembersRequest request, StreamObserver<ListMembersResponse> responseObserver) {
        execute(responseObserver, () -> {
            NormalPage<MemberDto> page = memberUseCase.listMembers(request.getGymId(), request.getPage(), request.getLimit());
            List<GetMemberResponse> responses = page.items().stream().map(memberMapper::toGetMemberResponse).toList();
            return ListMembersResponse.newBuilder()
                    .addAllMembers(responses)
                    .setTotal((int) page.totalRecords())
                    .build();
        });
    }

    public void validateMembership(ValidateMembershipRequest request, StreamObserver<ValidateMembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            MembershipValidation validation = subscriptionLifecycleUseCase.validateMembership(
                    request.getUserId(), request.getGymId());
            return ValidateMembershipResponse.newBuilder()
                    .setMemberId(validation.memberId())
                    .setValid(validation.status() == MembershipStatus.ACTIVE)
                    .setStatus(ProtoEnums.toProto(validation.status()))
                    .build();
        });
    }

    public void listMembersByStatus(ListMembersByStatusRequest request, StreamObserver<ListMembersByStatusResponse> responseObserver) {
        execute(responseObserver, () -> {
            MembershipStatus status = ProtoEnums.toDomain(request.getStatus());
            List<MemberDto> dtos = memberUseCase.listMembersByStatus(status, request.getGymIdsList());
            List<GetMemberResponse> responses = dtos.stream().map(memberMapper::toGetMemberResponse).toList();
            return ListMembersByStatusResponse.newBuilder().addAllMembers(responses).build();
        });
    }
}
