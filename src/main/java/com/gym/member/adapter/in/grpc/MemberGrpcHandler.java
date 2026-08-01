package com.gym.member.adapter.in.grpc;

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
import com.gym.proto.member.v1.ResumeMembershipRequest;
import com.gym.proto.member.v1.UpdateGymLocationRequest;
import com.gym.proto.member.v1.UpdateProfileRequest;
import com.gym.proto.member.v1.ValidateMembershipRequest;
import com.gym.proto.member.v1.ValidateMembershipResponse;
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

    @Override
    public void getMember(GetMemberRequest request, StreamObserver<MemberResponse> responseObserver) {
        try {
            MemberDto dto = memberService.getMember(request.getMemberId());
            responseObserver.onNext(toMemberResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
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
            responseObserver.onNext(toMemberResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void listMembers(ListMembersRequest request, StreamObserver<ListMembersResponse> responseObserver) {
        try {
            var page = memberService.listMembers(request.getGymId(), request.getPage(), request.getLimit());
            List<MemberResponse> responses = page.getContent().stream().map(this::toMemberResponse).toList();
            ListMembersResponse response = ListMembersResponse.newBuilder()
                    .addAllMembers(responses)
                    .setTotal((int) page.getTotalElements())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getPlans(GetPlansRequest request, StreamObserver<PlansResponse> responseObserver) {
        try {
            List<PlanDto> plans = gymLocationService.getPlans(request.getGymId());
            List<Plan> planProtos = plans.stream().map(p -> Plan.newBuilder()
                    .setId(p.id().toString())
                    .setName(p.name())
                    .setPlanType(p.planType().name())
                    .setDurationDays(p.durationDays() != null ? p.durationDays() : 0)
                    .setPriceVnd(p.priceVnd())
                    .setDescription(p.description() != null ? p.description() : "")
                    .setActive(p.active())
                    .build()).toList();

            responseObserver.onNext(PlansResponse.newBuilder().addAllPlans(planProtos).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void pauseMembership(PauseMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            SubscriptionDto dto = subscriptionService.pauseSubscription(request.getMemberId());
            responseObserver.onNext(toMembershipResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void resumeMembership(ResumeMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            SubscriptionDto dto = subscriptionService.resumeSubscription(request.getMemberId());
            responseObserver.onNext(toMembershipResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getMembershipStatus(GetMembershipStatusRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            SubscriptionDto dto = subscriptionService.getActiveSubscription(request.getMemberId());
            responseObserver.onNext(toMembershipResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
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
            responseObserver.onNext(toGymLocationResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
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
            responseObserver.onNext(toGymLocationResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void listGymLocations(ListGymLocationsRequest request, StreamObserver<GymLocationsResponse> responseObserver) {
        try {
            List<GymLocationDto> dtos = gymLocationService.listGymLocations(request.getChainId());
            List<GymLocationResponse> responses = dtos.stream().map(this::toGymLocationResponse).toList();
            responseObserver.onNext(GymLocationsResponse.newBuilder().addAllLocations(responses).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getGymLocation(GetGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        try {
            GymLocationDto dto = gymLocationService.getGymLocation(request.getId());
            responseObserver.onNext(toGymLocationResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
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
            responseObserver.onError(e);
        }
    }

    @Override
    public void getGymDailySecret(GetGymDailySecretRequest request, StreamObserver<GymDailySecretResponse> responseObserver) {
        try {
            GymDailySecretDto dto = gymQRService.getGymDailySecret(request.getGymId());
            responseObserver.onNext(GymDailySecretResponse.newBuilder().setDailySecret(dto.dailySecret()).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void listMembersByStatus(ListMembersByStatusRequest request, StreamObserver<ListMembersByStatusResponse> responseObserver) {
        try {
            MembershipStatus status = MembershipStatus.valueOf(request.getStatus());
            List<MemberDto> dtos = memberService.listMembersByStatus(status, request.getGymIdsList());
            List<MemberResponse> responses = dtos.stream().map(this::toMemberResponse).toList();
            responseObserver.onNext(ListMembersByStatusResponse.newBuilder().addAllMembers(responses).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private MemberResponse toMemberResponse(MemberDto dto) {
        return MemberResponse.newBuilder()
                .setId(dto.id().toString())
                .setUserId(dto.userId().toString())
                .setGymId(dto.gymId().toString())
                .setFullName(dto.fullName())
                .setPhone(dto.phone() != null ? dto.phone() : "")
                .setAvatarUrl(dto.avatarUrl() != null ? dto.avatarUrl() : "")
                .setEmergencyContact(dto.emergencyContact() != null ? dto.emergencyContact() : "")
                .setStatus(dto.status().name())
                .build();
    }

    private MembershipResponse toMembershipResponse(SubscriptionDto dto) {
        return MembershipResponse.newBuilder()
                .setMemberId(dto.memberId().toString())
                .setStatus(dto.status().name())
                .setStartDate(dto.startDate() != null ? dto.startDate().toString() : "")
                .setEndDate(dto.endDate() != null ? dto.endDate().toString() : "")
                .setRemainingDays(dto.remainingDays() != null ? dto.remainingDays() : 0)
                .build();
    }

    private GymLocationResponse toGymLocationResponse(GymLocationDto dto) {
        return GymLocationResponse.newBuilder()
                .setId(dto.id().toString())
                .setChainId(dto.chainId().toString())
                .setName(dto.name())
                .setAddress(dto.address())
                .setCity(dto.city())
                .setStatus(dto.status())
                .build();
    }
}
