package com.gym.member.adapter.in.grpc;

import com.gym.common.grpc.security.RequireRole;
import com.gym.common.pagination.NormalPage;
import com.gym.member.application.service.MemberService;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.mapper.MemberMapper;
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
import static com.gym.member.adapter.in.grpc.GrpcErrorHandler.handleError;

@Component
@RequiredArgsConstructor
public class MemberGrpcDelegate {

    private final MemberService memberService;
    private final MemberMapper memberMapper;

    @RequireRole("CUSTOMER")
    public void getMember(GetMemberRequest request, StreamObserver<MemberResponse> responseObserver) {
        try {
            MemberDto dto = memberService.getMember(request.getMemberId());
            requireSelf(dto);
            responseObserver.onNext(memberMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole("CUSTOMER")
    public void updateProfile(UpdateProfileRequest request, StreamObserver<MemberResponse> responseObserver) {
        try {
            MemberDto current = memberService.getMember(request.getMemberId());
            requireSelf(current);
            LocalDate dateOfBirth = (request.getDateOfBirth() != null && !request.getDateOfBirth().isBlank())
                    ? LocalDate.parse(request.getDateOfBirth())
                    : null;
            MemberDto dto = memberService.updateProfile(
                    request.getMemberId(),
                    request.getFullName(),
                    request.getPhone(),
                    request.getAvatarUrl(),
                    dateOfBirth
            );
            responseObserver.onNext(memberMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void listMembers(ListMembersRequest request, StreamObserver<ListMembersResponse> responseObserver) {
        try {
            if (!request.getGymId().isBlank()) {
                requireGym(request.getGymId());
            }
            NormalPage<MemberDto> page = memberService.listMembers(request.getGymId(), request.getPage(), request.getLimit());
            List<MemberResponse> responses = page.items().stream().map(memberMapper::toResponse).toList();
            ListMembersResponse response = ListMembersResponse.newBuilder()
                    .addAllMembers(responses)
                    .setTotal((int) page.totalRecords())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole("CHECKIN_SERVICE")
    public void validateMembership(ValidateMembershipRequest request, StreamObserver<ValidateMembershipResponse> responseObserver) {
        try {
            requireServiceGym(request.getGymId());
            MemberDto member = memberService.getMember(request.getMemberId());
            boolean valid = member.status() == MembershipStatus.ACTIVE && member.gymId().toString().equals(request.getGymId());
            ValidateMembershipResponse response = ValidateMembershipResponse.newBuilder()
                    .setValid(valid)
                    .setStatus(member.status().name())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole("NOTIFICATION_SERVICE")
    public void listMembersByStatus(ListMembersByStatusRequest request, StreamObserver<ListMembersByStatusResponse> responseObserver) {
        try {
            requireGymIds(request.getGymIdsList());
            MembershipStatus status = MembershipStatus.valueOf(request.getStatus());
            List<MemberDto> dtos = memberService.listMembersByStatus(status, request.getGymIdsList());
            List<MemberResponse> responses = dtos.stream().map(memberMapper::toResponse).toList();
            responseObserver.onNext(ListMembersByStatusResponse.newBuilder().addAllMembers(responses).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }
}
