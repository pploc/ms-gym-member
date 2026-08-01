package com.gym.member.integration.grpc;

import com.gym.member.adapter.in.grpc.MemberGrpcHandler;
import com.gym.member.adapter.out.persistence.entity.GymLocationEntity;
import com.gym.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.adapter.out.persistence.repository.GymLocationJpaRepository;
import com.gym.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.proto.member.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private MemberJpaRepository memberRepository;

    @Autowired
    private GymLocationJpaRepository gymLocationRepository;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private MemberServiceGrpc.MemberServiceBlockingStub blockingStub;

    private String memberId;
    private String gymId;

    @BeforeEach
    void setUp() throws Exception {
        gymId = UUID.randomUUID().toString();
        memberId = UUID.randomUUID().toString();

        GymLocationEntity location = new GymLocationEntity();
        location.setId(gymId);
        location.setChainId(UUID.randomUUID().toString());
        location.setName("Integration Gym Location");
        location.setAddress("456 Broadway");
        location.setCity("New York");
        location.setStatus("ACTIVE");
        gymLocationRepository.save(location);

        MemberEntity member = new MemberEntity();
        member.setId(memberId);
        member.setUserId(UUID.randomUUID().toString());
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
                .addService(memberGrpcHandler)
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

    @Test
    void givenExistingMember_whenGetMember_thenReturnsMemberResponse() {
        // Given
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId).build();

        // When
        MemberResponse response = blockingStub.getMember(request);

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

        // When
        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> blockingStub.getMember(request)
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

        // When
        MemberResponse response = blockingStub.updateProfile(request);

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

        // When
        ListMembersResponse response = blockingStub.listMembers(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getMembers(0).getId()).isEqualTo(memberId);
    }
}
