package com.gym.member.member.adapter.in.grpc;

import com.gym.common.grpc.security.RequirePolicy;
import com.gym.common.grpc.security.RequireRole;
import com.gym.common.grpc.security.RpcPolicyKind;
import com.gym.member.location.adapter.in.grpc.GymLocationGrpcDelegate;

import com.gym.proto.member.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberGrpcHandler extends MemberServiceGrpc.MemberServiceImplBase {

    private final MemberGrpcDelegate memberGrpcDelegate;
    private final SubscriptionGrpcDelegate subscriptionGrpcDelegate;
    private final GymLocationGrpcDelegate gymLocationGrpcDelegate;

    @Override
    @RequireRole("CUSTOMER")
    public void getMember(GetMemberRequest request, StreamObserver<MemberResponse> responseObserver) {
        memberGrpcDelegate.getMember(request, responseObserver);
    }

    @Override
    @RequireRole("CUSTOMER")
    public void updateProfile(UpdateProfileRequest request, StreamObserver<MemberResponse> responseObserver) {
        memberGrpcDelegate.updateProfile(request, responseObserver);
    }

    @Override
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void listMembers(ListMembersRequest request, StreamObserver<ListMembersResponse> responseObserver) {
        memberGrpcDelegate.listMembers(request, responseObserver);
    }

    @Override
    @RequireRole({"CUSTOMER", "ADMIN", "SUPER_ADMIN"})
    public void getPlans(GetPlansRequest request, StreamObserver<PlansResponse> responseObserver) {
        gymLocationGrpcDelegate.getPlans(request, responseObserver);
    }

    @Override
    @RequireRole("CUSTOMER")
    public void purchaseMembership(PurchaseMembershipRequest request, StreamObserver<PurchaseResponse> responseObserver) {
        subscriptionGrpcDelegate.purchaseMembership(request, responseObserver);
    }

    @Override
    @RequireRole("CUSTOMER")
    public void pauseMembership(PauseMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.pauseMembership(request, responseObserver);
    }

    @Override
    @RequireRole("CUSTOMER")
    public void resumeMembership(ResumeMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.resumeMembership(request, responseObserver);
    }

    @Override
    @RequireRole("CUSTOMER")
    public void getMembershipStatus(GetMembershipStatusRequest request, StreamObserver<MembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.getMembershipStatus(request, responseObserver);
    }

    @Override
    @RequirePolicy(RpcPolicyKind.INTERNAL_WORKLOAD)
    public void getMembershipStatusByUserId(GetMembershipStatusByUserIdRequest request, StreamObserver<MembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.getMembershipStatusByUserId(request, responseObserver);
    }

    @Override
    @RequireRole("SUPER_ADMIN")
    public void createGymLocation(CreateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        gymLocationGrpcDelegate.createGymLocation(request, responseObserver);
    }

    @Override
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void updateGymLocation(UpdateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        gymLocationGrpcDelegate.updateGymLocation(request, responseObserver);
    }

    @Override
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void listGymLocations(ListGymLocationsRequest request, StreamObserver<GymLocationsResponse> responseObserver) {
        gymLocationGrpcDelegate.listGymLocations(request, responseObserver);
    }

    @Override
    @RequireRole({"CUSTOMER", "TRAINER", "ADMIN", "SUPER_ADMIN"})
    public void getGymLocation(GetGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        gymLocationGrpcDelegate.getGymLocation(request, responseObserver);
    }

    @Override
    @RequireRole("CHECKIN_SERVICE")
    public void validateMembership(ValidateMembershipRequest request, StreamObserver<ValidateMembershipResponse> responseObserver) {
        memberGrpcDelegate.validateMembership(request, responseObserver);
    }

    @Override
    @RequireRole("NOTIFICATION_SERVICE")
    public void listMembersByStatus(ListMembersByStatusRequest request, StreamObserver<ListMembersByStatusResponse> responseObserver) {
        memberGrpcDelegate.listMembersByStatus(request, responseObserver);
    }
}
