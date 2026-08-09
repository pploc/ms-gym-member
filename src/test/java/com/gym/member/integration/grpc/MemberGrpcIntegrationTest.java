package com.gym.member.integration.grpc;

import com.gym.common.grpc.interceptor.AuthServerInterceptor;
import com.gym.common.grpc.interceptor.ExceptionInterceptor;
import com.gym.common.grpc.interceptor.LoggingInterceptor;
import com.gym.common.grpc.interceptor.MetricsInterceptor;
import com.gym.common.grpc.interceptor.TracingInterceptor;
import com.gym.member.member.adapter.in.grpc.MemberGrpcHandler;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.proto.member.v1.GetMemberRequest;
import com.gym.proto.member.v1.GetMembershipStatusByUserIdRequest;
import com.gym.proto.member.v1.ListMembersRequest;
import com.gym.proto.member.v1.ListMembersResponse;
import com.gym.proto.member.v1.MemberResponse;
import com.gym.proto.member.v1.MemberServiceGrpc;
import com.gym.proto.member.v1.MembershipResponse;
import com.gym.proto.member.v1.UpdateProfileRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberGrpcIntegrationTest {

    @Autowired
    private MemberGrpcHandler memberGrpcHandler;

    @Autowired
    private com.gym.common.grpc.interceptor.GrpcMethodRegistry methodRegistry;

    @Autowired
    private ExceptionInterceptor exceptionInterceptor;

    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Autowired
    private TracingInterceptor tracingInterceptor;

    @Autowired
    private MetricsInterceptor metricsInterceptor;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private SubscriptionJpaRepository subscriptionRepository;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private MemberServiceGrpc.MemberServiceBlockingStub blockingStub;

    private String memberId;
    private String userId;
    private String gymId;

    @BeforeEach
    void setUp() throws Exception {
        gymId = UUID.randomUUID().toString();
        memberId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();

        MemberEntity member = new MemberEntity();
        member.setId(memberId);
        member.setUserId(userId);
        member.setFullName("John Real DB");
        member.setPhone("12345678");
        member.setStatus(MembershipStatus.ACTIVE);
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        memberRepository.save(member);

        SubscriptionEntity sub = new SubscriptionEntity();
        sub.setId(UUID.randomUUID().toString());
        sub.setMemberId(memberId);
        sub.setGymId(gymId);
        sub.setPlanId(UUID.randomUUID().toString());
        sub.setPlanTypeSnapshot(PlanType.MONTHLY);
        sub.setDurationDaysSnapshot(30);
        sub.setPriceVndSnapshot(500_000L);
        sub.setStatus(MembershipStatus.ACTIVE);
        sub.setStartDate(java.time.LocalDate.now());
        sub.setEndDate(java.time.LocalDate.now().plusDays(30));
        subscriptionRepository.save(sub);

        String serverName = InProcessServerBuilder.generateName();
        AuthServerInterceptor testAuthInterceptor = new AuthServerInterceptor(methodRegistry, call -> true);

        inProcessServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(
                        memberGrpcHandler,
                        List.of(
                                tracingInterceptor,
                                loggingInterceptor,
                                metricsInterceptor,
                                exceptionInterceptor,
                                testAuthInterceptor)))
                .build()
                .start();

        inProcessChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        blockingStub = MemberServiceGrpc.newBlockingStub(inProcessChannel);
    }

    @AfterEach
    void tearDown() {
        if (inProcessChannel != null) {
            inProcessChannel.shutdownNow();
        }
        if (inProcessServer != null) {
            inProcessServer.shutdownNow();
        }
    }

    private MemberServiceGrpc.MemberServiceBlockingStub getStubWithHeaders(String uId, String role, String gId) {
        Metadata headers = new Metadata();
        if (uId != null) {
            headers.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), uId);
        }
        if (role != null) {
            headers.put(Metadata.Key.of("x-user-role", Metadata.ASCII_STRING_MARSHALLER), role);
        }
        if (gId != null) {
            headers.put(Metadata.Key.of("x-gym-id", Metadata.ASCII_STRING_MARSHALLER), gId);
        }
        return blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @Test
    void givenExistingMember_whenGetMember_thenReturnsMemberResponse() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId).build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(userId, "CUSTOMER", gymId);

        MemberResponse response = stub.getMember(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(memberId);
        assertThat(response.getFullName()).isEqualTo("John Real DB");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void givenNonExistentMember_whenGetMember_thenThrowsNotFoundStatus() {
        String nonExistentId = UUID.randomUUID().toString();
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(nonExistentId).build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(userId, "CUSTOMER", gymId);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> stub.getMember(request));

        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void givenValidProfileUpdateRequest_whenUpdateProfile_thenUpdatesMemberInDatabase() {
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder()
                .setMemberId(memberId)
                .setFullName("Jane Real DB")
                .setPhone("87654321")
                .build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(userId, "CUSTOMER", gymId);

        MemberResponse response = stub.updateProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.getFullName()).isEqualTo("Jane Real DB");
        assertThat(response.getPhone()).isEqualTo("87654321");

        MemberEntity updatedInDb = memberRepository.findById(memberId).orElse(null);
        assertThat(updatedInDb).isNotNull();
        assertThat(updatedInDb.getFullName()).isEqualTo("Jane Real DB");
    }

    @Test
    void givenExistingGymMembers_whenListMembers_thenReturnsMembersListResponse() {
        ListMembersRequest request = ListMembersRequest.newBuilder()
                .setGymId(gymId)
                .setPage(0)
                .setLimit(10)
                .build();
        MemberServiceGrpc.MemberServiceBlockingStub stub =
                getStubWithHeaders(UUID.randomUUID().toString(), "ADMIN", gymId);

        ListMembersResponse response = stub.listMembers(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getMembers(0).getId()).isEqualTo(memberId);
    }

    @Test
    void givenUnauthenticatedUser_whenGetMember_thenThrowsPermissionDenied() {
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId).build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(null, null, null);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> stub.getMember(request));

        assertThat(exception.getStatus().getCode()).isIn(Status.Code.PERMISSION_DENIED, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void givenWrongRole_whenListMembers_thenThrowsPermissionDenied() {
        ListMembersRequest request = ListMembersRequest.newBuilder()
                .setGymId(gymId)
                .setPage(0)
                .setLimit(10)
                .build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(userId, "CUSTOMER", gymId);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> stub.listMembers(request));

        assertThat(exception.getStatus().getCode())
                .isIn(Status.Code.PERMISSION_DENIED, Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void givenDifferentUserScope_whenGetMember_thenThrowsPermissionDenied() {
        String otherUserId = UUID.randomUUID().toString();
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId).build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(otherUserId, "CUSTOMER", gymId);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> stub.getMember(request));

        assertThat(exception.getStatus().getCode())
                .isIn(Status.Code.PERMISSION_DENIED, Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void givenDifferentGymScope_whenListMembers_thenThrowsPermissionDenied() {
        String otherGymId = UUID.randomUUID().toString();
        ListMembersRequest request = ListMembersRequest.newBuilder()
                .setGymId(gymId)
                .setPage(0)
                .setLimit(10)
                .build();
        MemberServiceGrpc.MemberServiceBlockingStub stub =
                getStubWithHeaders(UUID.randomUUID().toString(), "ADMIN", otherGymId);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> stub.listMembers(request));

        assertThat(exception.getStatus().getCode())
                .isIn(Status.Code.PERMISSION_DENIED, Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void givenAdminRole_whenGetMembershipStatusByUserId_thenReturnsSuccess() {
        GetMembershipStatusByUserIdRequest request = GetMembershipStatusByUserIdRequest.newBuilder()
                .setUserId(userId)
                .setGymId(gymId)
                .build();

        MembershipResponse response = blockingStub.getMembershipStatusByUserId(request);
        assertThat(response).isNotNull();
        assertThat(response.getMemberId()).isEqualTo(memberId);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }
}
