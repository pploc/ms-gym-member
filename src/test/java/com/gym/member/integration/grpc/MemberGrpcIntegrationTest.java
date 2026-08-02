package com.gym.member.integration.grpc;

import com.gym.common.grpc.interceptor.AuthServerInterceptor;
import com.gym.common.grpc.interceptor.ExceptionInterceptor;
import com.gym.common.grpc.interceptor.LoggingInterceptor;
import com.gym.common.grpc.interceptor.MetricsInterceptor;
import com.gym.common.grpc.interceptor.TracingInterceptor;
import com.gym.member.member.adapter.in.grpc.MemberGrpcHandler;
import com.gym.member.location.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.location.domain.model.GymLocationStatus;
import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.location.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.proto.member.v1.*;
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
    private AuthServerInterceptor authServerInterceptor;

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
    private GymLocationJpaRepository gymLocationRepository;

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

        GymLocationEntity location = new GymLocationEntity();
        location.setId(gymId);
        location.setChainId(UUID.randomUUID().toString());
        location.setName("Integration Gym Location");
        location.setAddress("456 Broadway");
        location.setCity("New York");
        location.setStatus(GymLocationStatus.ACTIVE);
        gymLocationRepository.save(location);

        MemberEntity member = new MemberEntity();
        member.setId(memberId);
        member.setUserId(userId);
        member.setGymId(gymId);
        member.setFullName("John Real DB");
        member.setPhone("12345678");
        member.setStatus(MembershipStatus.ACTIVE);
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        memberRepository.save(member);

        String serverName = InProcessServerBuilder.generateName();

        inProcessServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(
                        memberGrpcHandler,
                        List.of(tracingInterceptor, loggingInterceptor, metricsInterceptor, exceptionInterceptor, authServerInterceptor)
                ))
                .build()
                .start();

        inProcessChannel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        blockingStub = MemberServiceGrpc.newBlockingStub(inProcessChannel);
    }

    @AfterEach
    void tearDown() {
        if (inProcessChannel != null) inProcessChannel.shutdownNow();
        if (inProcessServer != null) inProcessServer.shutdownNow();
    }

    private MemberServiceGrpc.MemberServiceBlockingStub getStubWithHeaders(String uId, String role, String gId) {
        Metadata headers = new Metadata();
        if (uId != null) headers.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), uId);
        if (role != null) headers.put(Metadata.Key.of("x-user-role", Metadata.ASCII_STRING_MARSHALLER), role);
        if (gId != null) headers.put(Metadata.Key.of("x-gym-id", Metadata.ASCII_STRING_MARSHALLER), gId);
        return blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @Test
    void givenExistingMember_whenGetMember_thenReturnsMemberResponse() {
        // Given
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId).build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(userId, "CUSTOMER", gymId);

        // When
        MemberResponse response = stub.getMember(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(memberId);
        assertThat(response.getFullName()).isEqualTo("John Real DB");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void givenNonExistentMember_whenGetMember_thenThrowsNotFoundStatus() {
        // Given
        String nonExistentId = UUID.randomUUID().toString();
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(nonExistentId).build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(userId, "CUSTOMER", gymId);

        // When
        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> stub.getMember(request)
        );

        // Then
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void givenValidProfileUpdateRequest_whenUpdateProfile_thenUpdatesMemberInDatabase() {
        // Given
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder()
                .setMemberId(memberId)
                .setFullName("Jane Real DB")
                .setPhone("87654321")
                .build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(userId, "CUSTOMER", gymId);

        // When
        MemberResponse response = stub.updateProfile(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFullName()).isEqualTo("Jane Real DB");
        assertThat(response.getPhone()).isEqualTo("87654321");

        MemberEntity updatedInDb = memberRepository.findById(memberId).orElse(null);
        assertThat(updatedInDb).isNotNull();
        assertThat(updatedInDb.getFullName()).isEqualTo("Jane Real DB");
    }

    @Test
    void givenExistingGymMembers_whenListMembers_thenReturnsMembersListResponse() {
        // Given
        ListMembersRequest request = ListMembersRequest.newBuilder()
                .setGymId(gymId)
                .setPage(0)
                .setLimit(10)
                .build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = getStubWithHeaders(UUID.randomUUID().toString(), "ADMIN", gymId);

        // When
        ListMembersResponse response = stub.listMembers(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getMembers(0).getId()).isEqualTo(memberId);
    }
}
