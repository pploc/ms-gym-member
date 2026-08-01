package com.gym.member.adapter.in.grpc;

import com.gym.common.error.DomainException;
import com.gym.common.error.NotFoundException;
import com.gym.common.pagination.NormalPage;
import com.gym.member.application.service.GymLocationService;
import com.gym.member.application.service.GymQRService;
import com.gym.member.application.service.MemberService;
import com.gym.member.application.service.SubscriptionService;
import com.gym.member.domain.dto.GymDailySecretDto;
import com.gym.member.domain.dto.GymLocationDto;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.dto.PlanDto;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.mapper.GymLocationMapper;
import com.gym.member.mapper.GymQRSecretMapper;
import com.gym.member.mapper.MemberMapper;
import com.gym.member.mapper.SubscriptionMapper;
import com.gym.proto.member.v1.CreateGymLocationRequest;
import com.gym.proto.member.v1.GetGymDailySecretRequest;
import com.gym.proto.member.v1.GetGymLocationRequest;
import com.gym.proto.member.v1.GetMemberRequest;
import com.gym.proto.member.v1.GetMembershipStatusRequest;
import com.gym.proto.member.v1.GetPlansRequest;
import com.gym.proto.member.v1.GymDailySecretResponse;
import com.gym.proto.member.v1.GymLocationResponse;
import com.gym.proto.member.v1.GymLocationsResponse;
import com.gym.proto.member.v1.ListGymLocationsRequest;
import com.gym.proto.member.v1.ListMembersByStatusRequest;
import com.gym.proto.member.v1.ListMembersByStatusResponse;
import com.gym.proto.member.v1.ListMembersRequest;
import com.gym.proto.member.v1.ListMembersResponse;
import com.gym.proto.member.v1.MemberResponse;
import com.gym.proto.member.v1.MemberServiceGrpc;
import com.gym.proto.member.v1.MembershipResponse;
import com.gym.proto.member.v1.PauseMembershipRequest;
import com.gym.proto.member.v1.Plan;
import com.gym.proto.member.v1.PlansResponse;
import com.gym.proto.member.v1.PurchaseMembershipRequest;
import com.gym.proto.member.v1.PurchaseResponse;
import com.gym.proto.member.v1.ResumeMembershipRequest;
import com.gym.proto.member.v1.UpdateGymLocationRequest;
import com.gym.proto.member.v1.UpdateProfileRequest;
import com.gym.proto.member.v1.ValidateMembershipRequest;
import com.gym.proto.member.v1.ValidateMembershipResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberGrpcHandler extends MemberServiceGrpc.MemberServiceImplBase {

    private final MemberService memberService;
    private final SubscriptionService subscriptionService;
    private final GymLocationService gymLocationService;
    private final GymQRService gymQRService;

    private final MemberMapper memberMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final GymLocationMapper gymLocationMapper;
    private final GymQRSecretMapper gymQRSecretMapper;

    private void handleError(StreamObserver<?> responseObserver, Exception e) {
        log.error("gRPC error: {}", e.getMessage(), e);
        Status status;
        if (e instanceof NotFoundException) {
            status = Status.NOT_FOUND.withDescription(e.getMessage());
        } else if (e instanceof IllegalArgumentException) {
            status = Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        } else if (e instanceof DomainException) {
            status = Status.FAILED_PRECONDITION.withDescription(e.getMessage());
        } else {
            status = Status.INTERNAL.withDescription(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        responseObserver.onError(status.asRuntimeException());
    }

    @Override
    public void getMember(GetMemberRequest request, StreamObserver<MemberResponse> responseObserver) {
        try {
            MemberDto dto = memberService.getMember(request.getMemberId());
            responseObserver.onNext(memberMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<MemberResponse> responseObserver) {
        try {
            MemberDto dto = memberService.updateProfile(
                    request.getMemberId(),
                    request.getFullName(),
                    request.getPhone(),
                    request.getAvatarUrl(),
                    request.getEmergencyContact()
            );
            responseObserver.onNext(memberMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void listMembers(ListMembersRequest request, StreamObserver<ListMembersResponse> responseObserver) {
        try {
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

    @Override
    public void getPlans(GetPlansRequest request, StreamObserver<PlansResponse> responseObserver) {
        try {
            List<PlanDto> plans = gymLocationService.getPlans(request.getGymId());
            List<Plan> planProtos = plans.stream().map(gymLocationMapper::toPlanProto).toList();

            responseObserver.onNext(PlansResponse.newBuilder().addAllPlans(planProtos).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void purchaseMembership(PurchaseMembershipRequest request, StreamObserver<PurchaseResponse> responseObserver) {
        try {
            String paymentId = java.util.UUID.randomUUID().toString();
            String paymentUrl = "https://payment.gym.com/checkout/" + paymentId;
            PurchaseResponse response = PurchaseResponse.newBuilder()
                    .setPaymentId(paymentId)
                    .setPaymentUrl(paymentUrl)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void pauseMembership(PauseMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            SubscriptionDto dto = subscriptionService.pauseSubscription(request.getMemberId());
            responseObserver.onNext(subscriptionMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void resumeMembership(ResumeMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            SubscriptionDto dto = subscriptionService.resumeSubscription(request.getMemberId());
            responseObserver.onNext(subscriptionMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void getMembershipStatus(GetMembershipStatusRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            SubscriptionDto dto = subscriptionService.getActiveSubscription(request.getMemberId());
            responseObserver.onNext(subscriptionMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void createGymLocation(CreateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        try {
            GymLocationDto dto = gymLocationService.createGymLocation(
                    request.getChainId(),
                    request.getName(),
                    request.getAddress(),
                    request.getCity()
            );
            responseObserver.onNext(gymLocationMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void updateGymLocation(UpdateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        try {
            GymLocationDto dto = gymLocationService.updateGymLocation(
                    request.getId(),
                    request.getName(),
                    request.getAddress(),
                    request.getCity(),
                    request.getStatus()
            );
            responseObserver.onNext(gymLocationMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void listGymLocations(ListGymLocationsRequest request, StreamObserver<GymLocationsResponse> responseObserver) {
        try {
            List<GymLocationDto> dtos = gymLocationService.listGymLocations(request.getChainId());
            List<GymLocationResponse> responses = dtos.stream().map(gymLocationMapper::toResponse).toList();
            responseObserver.onNext(GymLocationsResponse.newBuilder().addAllLocations(responses).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void getGymLocation(GetGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        try {
            GymLocationDto dto = gymLocationService.getGymLocation(request.getId());
            responseObserver.onNext(gymLocationMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void validateMembership(ValidateMembershipRequest request, StreamObserver<ValidateMembershipResponse> responseObserver) {
        try {
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

    @Override
    public void getGymDailySecret(GetGymDailySecretRequest request, StreamObserver<GymDailySecretResponse> responseObserver) {
        try {
            GymDailySecretDto dto = gymQRService.getGymDailySecret(request.getGymId());
            responseObserver.onNext(gymQRSecretMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @Override
    public void listMembersByStatus(ListMembersByStatusRequest request, StreamObserver<ListMembersByStatusResponse> responseObserver) {
        try {
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
