package com.gym.member.member.adapter.in.grpc;

import com.gym.common.grpc.security.GrpcSecurityContext;
import com.gym.member.member.adapter.out.persistence.mapper.SubscriptionMapper;
import com.gym.member.member.application.port.in.MemberUseCase;
import com.gym.member.member.application.port.in.MembershipPurchaseUseCase;
import com.gym.member.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.member.domain.dto.MemberDto;
import com.gym.member.member.domain.dto.SubscriptionDto;
import com.gym.proto.member.v1.GetMembershipStatusRequest;
import com.gym.proto.member.v1.GetMembershipStatusResponse;
import com.gym.proto.member.v1.PauseMembershipRequest;
import com.gym.proto.member.v1.PauseMembershipResponse;
import com.gym.proto.member.v1.PurchaseMembershipRequest;
import com.gym.proto.member.v1.PurchaseMembershipResponse;
import com.gym.proto.member.v1.ResumeMembershipRequest;
import com.gym.proto.member.v1.ResumeMembershipResponse;
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

    public void purchaseMembership(PurchaseMembershipRequest request, StreamObserver<PurchaseMembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            MemberDto member = memberUseCase.getMemberByUserId(GrpcSecurityContext.getUserId());
            requireSelf(member);
            return membershipPurchaseUseCase.purchaseMembership(
                    GrpcSecurityContext.getUserId(),
                    request.getGymId(),
                    request.getPurchase().getPlanId(),
                    request.getPurchase().getProvider(),
                    request.getPurchase().getDiscountCode(),
                    request.getPurchase().getIdempotencyKey());
        });
    }

    public void pauseMembership(PauseMembershipRequest request, StreamObserver<PauseMembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireSelf(memberUseCase.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.pauseSubscription(
                    request.getMemberId(), request.getGymId());
            return subscriptionMapper.toPauseResponse(dto);
        });
    }

    public void resumeMembership(ResumeMembershipRequest request, StreamObserver<ResumeMembershipResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireSelf(memberUseCase.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.resumeSubscription(
                    request.getMemberId(), request.getGymId());
            return subscriptionMapper.toResumeResponse(dto);
        });
    }

    public void getMembershipStatus(
            GetMembershipStatusRequest request, StreamObserver<GetMembershipStatusResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireSelf(memberUseCase.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.getActiveSubscription(
                    request.getMemberId(), request.getGymId());
            return subscriptionMapper.toStatusResponse(dto);
        });
    }
}
