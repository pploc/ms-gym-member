package com.gym.member.member.adapter.in.grpc;

import com.gym.common.grpc.security.RequireRole;
import com.gym.common.pagination.NormalPage;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.adapter.out.persistence.mapper.MemberMapper;
import com.gym.proto.member.v1.GetMemberRequest;
import com.gym.proto.member.v1.ListMembersByStatusRequest;
import com.gym.proto.member.v1.ListMembersByStatusResponse;
import com.gym.proto.member.v1.ListMembersRequest;
import com.gym.proto.member.v1.ListMembersResponse;
import com.gym.proto.member.v1.MemberResponse;
import com.gym.proto.member.v1.UpdateProfileRequest;
import com.gym.proto.member.v1.ValidateMembershipRequest;
import com.gym.proto.member.v1.ValidateMembershipResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireGym;
import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireGymIds;
import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireSelf;
import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireServiceGym;
import static com.gym.member.adapter.in.grpc.GrpcErrorHandler.execute;

@Component
@RequiredArgsConstructor
public class MemberGrpcDelegate {

    private final MemberUseCase memberUseCase;
    private final MemberMapper memberMapper;

    @RequireRole("CUSTOMER")
    public void getMember(GetMemberRequest request, StreamObserver<MemberResponse> responseObserver) {
        execute(responseObserver, () -> {
            MemberDto dto = memberUseCase.getMember(request.getMemberId());
            requireSelf(dto);
            return memberMapper.toResponse(dto);
        });
    }

    @RequireRole("CUSTOMER")
    public void updateProfile(UpdateProfileRequest request, StreamObserver<MemberResponse> responseObserver) {
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
            return memberMapper.toResponse(dto);
        });
    }

    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void listMembers(ListMembersRequest request, StreamObserver<ListMembersResponse> responseObserver) {
        execute(responseObserver, () -> {
            if (!request.getGymId().isBlank()) {
                requireGym(request.getGymId());
            }
            NormalPage<MemberDto> page = memberUseCase.listMembers(request.getGymId(), request.getPage(), request.getLimit());
            List<MemberResponse> responses = page.items().stream().map(memberMapper::toResponse).toList();
            return ListMembersResponse.newBuilder()
                    .addAllMembers(responses)
                    .setTotal((int) page.totalRecords())
                    .build();
        });
    }

    @RequireRole("CHECKIN_SERVICE")
    public void validateMembership(ValidateMembershipRequest request, StreamObserver<ValidateMembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireServiceGym(request.getGymId());
            MemberDto member = memberUseCase.getMember(request.getMemberId());
            boolean valid = member.status() == MembershipStatus.ACTIVE && member.gymId().toString().equals(request.getGymId());
            return ValidateMembershipResponse.newBuilder()
                    .setValid(valid)
                    .setStatus(member.status().name())
                    .build();
        });
    }

    @RequireRole("NOTIFICATION_SERVICE")
    public void listMembersByStatus(ListMembersByStatusRequest request, StreamObserver<ListMembersByStatusResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireGymIds(request.getGymIdsList());
            MembershipStatus status = MembershipStatus.valueOf(request.getStatus());
            List<MemberDto> dtos = memberUseCase.listMembersByStatus(status, request.getGymIdsList());
            List<MemberResponse> responses = dtos.stream().map(memberMapper::toResponse).toList();
            return ListMembersByStatusResponse.newBuilder().addAllMembers(responses).build();
        });
    }
}
