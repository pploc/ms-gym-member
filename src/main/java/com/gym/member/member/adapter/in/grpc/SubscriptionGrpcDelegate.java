package com.gym.member.member.adapter.in.grpc;

import com.gym.common.grpc.security.GrpcSecurityContext;
import com.gym.member.member.adapter.out.persistence.mapper.SubscriptionMapper;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.port.in.MembershipPurchaseUseCase;
import com.gym.member.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.proto.member.v1.GetMembershipStatusByUserIdRequest;
import com.gym.proto.member.v1.GetMembershipStatusRequest;
import com.gym.proto.member.v1.MembershipResponse;
import com.gym.proto.member.v1.PauseMembershipRequest;
import com.gym.proto.member.v1.PurchaseMembershipRequest;
import com.gym.proto.member.v1.PurchaseResponse;
import com.gym.proto.member.v1.ResumeMembershipRequest;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireSelf;
import static com.gym.member.adapter.in.grpc.GrpcErrorHandler.execute;

@Component
@RequiredArgsConstructor
public class SubscriptionGrpcDelegate {

    private final MemberUseCase memberUseCase;
    private final SubscriptionLifecycleUseCase subscriptionLifecycleUseCase;
    private final MembershipPurchaseUseCase membershipPurchaseUseCase;
    private final SubscriptionMapper subscriptionMapper;

    public void purchaseMembership(PurchaseMembershipRequest request, StreamObserver<PurchaseResponse> responseObserver) {
        execute(responseObserver, () -> {
            MemberDto member = memberUseCase.getMemberByUserId(GrpcSecurityContext.getUserId());
            requireSelf(member);
            return membershipPurchaseUseCase.purchaseMembership(
                    GrpcSecurityContext.getUserId(),
                    GrpcSecurityContext.getGymId(),
                    request.getPlanId(),
                    request.getProvider(),
                    request.getDiscountCode());
        });
    }

    public void pauseMembership(PauseMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireSelf(memberUseCase.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.pauseSubscription(
                    request.getMemberId(), GrpcSecurityContext.getGymId());
            return subscriptionMapper.toResponse(dto);
        });
    }

    public void resumeMembership(ResumeMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireSelf(memberUseCase.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.resumeSubscription(
                    request.getMemberId(), GrpcSecurityContext.getGymId());
            return subscriptionMapper.toResponse(dto);
        });
    }

    public void getMembershipStatus(
            GetMembershipStatusRequest request, StreamObserver<MembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireSelf(memberUseCase.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.getActiveSubscription(
                    request.getMemberId(), GrpcSecurityContext.getGymId());
            return subscriptionMapper.toResponse(dto);
        });
    }

    public void getMembershipStatusByUserId(
            GetMembershipStatusByUserIdRequest request, StreamObserver<MembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            SubscriptionDto dto = subscriptionLifecycleUseCase.getMembershipStatusByUserIdAndGymId(
                    request.getUserId(), request.getGymId());
            return subscriptionMapper.toResponse(dto);
        });
    }
}
