package com.gym.member.member.adapter.in.grpc;

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
    public void getMember(GetMemberRequest request, StreamObserver<MemberResponse> responseObserver) {
        memberGrpcDelegate.getMember(request, responseObserver);
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<MemberResponse> responseObserver) {
        memberGrpcDelegate.updateProfile(request, responseObserver);
    }

    @Override
    public void listMembers(ListMembersRequest request, StreamObserver<ListMembersResponse> responseObserver) {
        memberGrpcDelegate.listMembers(request, responseObserver);
    }

    @Override
    public void getPlans(GetPlansRequest request, StreamObserver<PlansResponse> responseObserver) {
        gymLocationGrpcDelegate.getPlans(request, responseObserver);
    }

    @Override
    public void purchaseMembership(PurchaseMembershipRequest request, StreamObserver<PurchaseResponse> responseObserver) {
        subscriptionGrpcDelegate.purchaseMembership(request, responseObserver);
    }

    @Override
    public void pauseMembership(PauseMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.pauseMembership(request, responseObserver);
    }

    @Override
    public void resumeMembership(ResumeMembershipRequest request, StreamObserver<MembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.resumeMembership(request, responseObserver);
    }

    @Override
    public void getMembershipStatus(GetMembershipStatusRequest request, StreamObserver<MembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.getMembershipStatus(request, responseObserver);
    }

    @Override
    public void createGymLocation(CreateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        gymLocationGrpcDelegate.createGymLocation(request, responseObserver);
    }

    @Override
    public void updateGymLocation(UpdateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        gymLocationGrpcDelegate.updateGymLocation(request, responseObserver);
    }

    @Override
    public void listGymLocations(ListGymLocationsRequest request, StreamObserver<GymLocationsResponse> responseObserver) {
        gymLocationGrpcDelegate.listGymLocations(request, responseObserver);
    }

    @Override
    public void getGymLocation(GetGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        gymLocationGrpcDelegate.getGymLocation(request, responseObserver);
    }

    @Override
    public void validateMembership(ValidateMembershipRequest request, StreamObserver<ValidateMembershipResponse> responseObserver) {
        memberGrpcDelegate.validateMembership(request, responseObserver);
    }

    @Override
    public void listMembersByStatus(ListMembersByStatusRequest request, StreamObserver<ListMembersByStatusResponse> responseObserver) {
        memberGrpcDelegate.listMembersByStatus(request, responseObserver);
    }
}
