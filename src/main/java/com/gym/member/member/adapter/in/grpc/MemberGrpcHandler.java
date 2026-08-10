package com.gym.member.member.adapter.in.grpc;

import com.gym.common.grpc.security.RequirePolicy;
import com.gym.common.grpc.security.RequireRole;
import com.gym.common.grpc.security.RpcPolicyKind;
import com.gym.proto.member.v1.GetMemberRequest;
import com.gym.proto.member.v1.GetMemberResponse;
import com.gym.proto.member.v1.GetMembershipStatusRequest;
import com.gym.proto.member.v1.GetMembershipStatusResponse;
import com.gym.proto.member.v1.ListMembersByStatusRequest;
import com.gym.proto.member.v1.ListMembersByStatusResponse;
import com.gym.proto.member.v1.ListMembersRequest;
import com.gym.proto.member.v1.ListMembersResponse;
import com.gym.proto.member.v1.MemberServiceGrpc;
import com.gym.proto.member.v1.PauseMembershipRequest;
import com.gym.proto.member.v1.PauseMembershipResponse;
import com.gym.proto.member.v1.PurchaseMembershipRequest;
import com.gym.proto.member.v1.PurchaseMembershipResponse;
import com.gym.proto.member.v1.ResumeMembershipRequest;
import com.gym.proto.member.v1.ResumeMembershipResponse;
import com.gym.proto.member.v1.UpdateProfileRequest;
import com.gym.proto.member.v1.UpdateProfileResponse;
import com.gym.proto.member.v1.ValidateMembershipRequest;
import com.gym.proto.member.v1.ValidateMembershipResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberGrpcHandler extends MemberServiceGrpc.MemberServiceImplBase {

    private final MemberGrpcDelegate memberGrpcDelegate;
    private final SubscriptionGrpcDelegate subscriptionGrpcDelegate;

    @Override
    @RequireRole({"CUSTOMER", "SUPER_ADMIN"})
    public void getMember(GetMemberRequest request, StreamObserver<GetMemberResponse> responseObserver) {
        memberGrpcDelegate.getMember(request, responseObserver);
    }

    @Override
    @RequireRole({"CUSTOMER", "SUPER_ADMIN"})
    public void updateProfile(UpdateProfileRequest request, StreamObserver<UpdateProfileResponse> responseObserver) {
        memberGrpcDelegate.updateProfile(request, responseObserver);
    }

    @Override
    @RequireRole("SUPER_ADMIN")
    public void listMembers(ListMembersRequest request, StreamObserver<ListMembersResponse> responseObserver) {
        memberGrpcDelegate.listMembers(request, responseObserver);
    }

    @Override
    @RequireRole("CUSTOMER")
    public void purchaseMembership(
            PurchaseMembershipRequest request, StreamObserver<PurchaseMembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.purchaseMembership(request, responseObserver);
    }

    @Override
    @RequireRole("CUSTOMER")
    public void pauseMembership(PauseMembershipRequest request, StreamObserver<PauseMembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.pauseMembership(request, responseObserver);
    }

    @Override
    @RequireRole("CUSTOMER")
    public void resumeMembership(ResumeMembershipRequest request, StreamObserver<ResumeMembershipResponse> responseObserver) {
        subscriptionGrpcDelegate.resumeMembership(request, responseObserver);
    }

    @Override
    @RequireRole({"CUSTOMER", "SUPER_ADMIN"})
    public void getMembershipStatus(
            GetMembershipStatusRequest request, StreamObserver<GetMembershipStatusResponse> responseObserver) {
        subscriptionGrpcDelegate.getMembershipStatus(request, responseObserver);
    }

    @Override
    @RequirePolicy(RpcPolicyKind.INTERNAL_WORKLOAD)
    public void validateMembership(
            ValidateMembershipRequest request, StreamObserver<ValidateMembershipResponse> responseObserver) {
        memberGrpcDelegate.validateMembership(request, responseObserver);
    }

    @Override
    @RequirePolicy(RpcPolicyKind.INTERNAL_WORKLOAD)
    public void listMembersByStatus(
            ListMembersByStatusRequest request, StreamObserver<ListMembersByStatusResponse> responseObserver) {
        memberGrpcDelegate.listMembersByStatus(request, responseObserver);
    }
}
