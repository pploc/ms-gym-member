package com.gym.member.integration.grpc;

import com.gym.member.adapter.in.grpc.MemberGrpcHandler;
import com.gym.member.application.service.MemberService;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.proto.member.v1.GetMemberRequest;
import com.gym.proto.member.v1.MemberResponse;
import com.gym.proto.member.v1.MemberServiceGrpc;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class MemberGrpcIntegrationTest {

    @Autowired
    private MemberGrpcHandler memberGrpcHandler;

    @MockitoBean
    private MemberService memberService;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private MemberServiceGrpc.MemberServiceBlockingStub blockingStub;

    private String memberId;
    private MemberDto memberDto;

    @BeforeEach
    void setUp() throws Exception {
        memberId = UUID.randomUUID().toString();
        memberDto = new MemberDto(
                UUID.fromString(memberId),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "John Integration",
                "123456789",
                "http://avatar.jpg",
                "987654321",
                MembershipStatus.ACTIVE,
                null,
                null
        );

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
        if (inProcessChannel != null) {
            inProcessChannel.shutdownNow();
        }
        if (inProcessServer != null) {
            inProcessServer.shutdownNow();
        }
    }

    @Test
    void getMember_integrationSuccess() {
        when(memberService.getMember(memberId)).thenReturn(memberDto);

        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId).build();
        MemberResponse response = blockingStub.getMember(request);

        assertNotNull(response);
        assertEquals(memberId, response.getId());
        assertEquals("John Integration", response.getFullName());
    }

    @Test
    void getMember_integrationNotFound_returnsGrpcNotFoundStatus() {
        when(memberService.getMember(memberId)).thenThrow(new com.gym.common.error.NotFoundException("Member not found"));

        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId).build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> blockingStub.getMember(request)
        );

        assertEquals(Status.Code.NOT_FOUND, exception.getStatus().getCode());
        assertTrue(exception.getMessage().contains("Member not found"));
    }
}
