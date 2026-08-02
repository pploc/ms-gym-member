package com.gym.member.adapter.in.grpc;

import com.gym.common.grpc.security.GrpcSecurityContext;
import com.gym.common.grpc.security.RequireRole;
import com.gym.member.adapter.out.grpc.PaymentGrpcClient;
import com.gym.member.application.port.in.SubscriptionLifecycleUseCase;
import com.gym.member.application.service.MemberService;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.mapper.SubscriptionMapper;
import com.gym.proto.member.v1.GetMembershipStatusRequest;
import com.gym.proto.member.v1.MembershipResponse;
import com.gym.proto.member.v1.PauseMembershipRequest;
import com.gym.proto.member.v1.PurchaseMembershipRequest;
import com.gym.proto.member.v1.PurchaseResponse;
import com.gym.proto.member.v1.ResumeMembershipRequest;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireGym;
import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireSelf;
import static com.gym.member.adapter.in.grpc.GrpcErrorHandler.handleError;

@Component
@RequiredArgsConstructor
public class SubscriptionGrpcDelegate {

    private final MemberService memberService;
    private final SubscriptionLifecycleUseCase subscriptionLifecycleUseCase;
    private final PaymentGrpcClient paymentGrpcClient;
    private final SubscriptionMapper subscriptionMapper;

    @RequireRole("CUSTOMER")
    public void purchaseMembership(PurchaseMembershipRequest request, StreamObserver<PurchaseResponse> responseObserver) {
        try {
            MemberDto member = memberService.getMemberByUserId(GrpcSecurityContext.getUserId());
            requireSelf(member);
            requireGym(member.gymId().toString());
            InitiatePaymentResponse payment = paymentGrpcClient.initiatePayment(InitiatePaymentRequest.newBuilder()
                    .setGymId(member.gymId().toString())
                    .setPaymentType("MEMBERSHIP")
                    .setReferenceId(request.getPlanId())
                    .setProvider(request.getProvider())
                    .setDiscountCode(request.getDiscountCode())
                    .build());
            responseObserver.onNext(PurchaseResponse.newBuilder()
                    .setPaymentId(payment.getPaymentId())
                    .setPaymentUrl(payment.getPaymentUrl())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole("CUSTOMER")
    public void pauseMembership(PauseMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            requireSelf(memberService.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.pauseSubscription(request.getMemberId());
            responseObserver.onNext(subscriptionMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole("CUSTOMER")
    public void resumeMembership(ResumeMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            requireSelf(memberService.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.resumeSubscription(request.getMemberId());
            responseObserver.onNext(subscriptionMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole("CUSTOMER")
    public void getMembershipStatus(GetMembershipStatusRequest request, StreamObserver<MembershipResponse> responseObserver) {
        try {
            requireSelf(memberService.getMember(request.getMemberId()));
            SubscriptionDto dto = subscriptionLifecycleUseCase.getActiveSubscription(request.getMemberId());
            responseObserver.onNext(subscriptionMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }
}
