package com.gym.member.integration.grpc;

import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.entity.SubscriptionEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.domain.model.MembershipStatus;
import com.gym.member.member.domain.model.PlanType;
import com.gym.proto.member.v1.MemberServiceGrpc;
import com.gym.proto.member.v1.ValidateMembershipRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.net.ssl.SSLException;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"test", "mtls"})
@EnabledIf("localCertsPresent")
class MemberMtlsWorkloadIntegrationTest {

    private static final java.nio.file.Path CERT_DIR = java.nio.file.Path.of("certs/local");

    static boolean localCertsPresent() {
        return List.of(
                        "server.crt",
                        "server.key",
                        "ca.crt",
                        "client-gateway.crt",
                        "client-gateway.key",
                        "client-kong.crt",
                        "client-kong.key",
                        "client-postman.crt",
                        "client-postman.key",
                        "client-identifier.crt",
                        "client-identifier.key",
                        "client-member.crt",
                        "client-member.key",
                        "client-checkin.crt",
                        "client-checkin.key",
                        "client-notification.crt",
                        "client-notification.key")
                .stream()
                .allMatch(file -> java.nio.file.Files.isRegularFile(CERT_DIR.resolve(file)));
    }

    @Value("${grpc.server.port}")
    private int grpcPort;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private SubscriptionJpaRepository subscriptionRepository;

    private final List<ManagedChannel> channels = new ArrayList<>();
    private String memberId;
    private String userId;
    private String gymId;

    @BeforeEach
    void setUpMember() {
        memberId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        gymId = UUID.randomUUID().toString();

        MemberEntity member = new MemberEntity();
        member.setId(memberId);
        member.setUserId(userId);
        member.setFullName("mTLS member");
        member.setStatus(MembershipStatus.ACTIVE);
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        memberRepository.save(member);

        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID().toString());
        subscription.setMemberId(memberId);
        subscription.setGymId(gymId);
        subscription.setPlanId(UUID.randomUUID().toString());
        subscription.setPlanTypeSnapshot(PlanType.MONTHLY);
        subscription.setDurationDaysSnapshot(30);
        subscription.setPriceVndSnapshot(500_000L);
        subscription.setStatus(MembershipStatus.ACTIVE);
        subscription.setStartDate(LocalDate.now());
        subscription.setEndDate(LocalDate.now().plusDays(30));
        subscriptionRepository.save(subscription);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
            channel.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void givenCheckinCertificate_whenValidateMembership_thenAllowsCanonicalResult() throws Exception {
        // given
        MemberServiceGrpc.MemberServiceBlockingStub stub = stubWith("client-checkin");

        // when
        var response = stub.validateMembership(request());

        // then
        assertEquals(memberId, response.getMemberId());
        assertEquals(com.gym.proto.common.v1.MembershipStatus.MEMBERSHIP_STATUS_ACTIVE, response.getStatus());
        assertTrue(response.getValid());
    }

    @Test
    void givenWrongWorkloadCertificate_whenValidateMembership_thenDenies() throws Exception {
        // given
        for (String clientName : List.of(
                "client-gateway", "client-kong", "client-postman", "client-identifier", "client-member", "client-notification")) {
            MemberServiceGrpc.MemberServiceBlockingStub stub = stubWith(clientName);

            // when
            StatusRuntimeException exception =
                    assertThrows(StatusRuntimeException.class, () -> stub.validateMembership(request()), clientName);

            // then
            assertEquals(Status.Code.PERMISSION_DENIED, exception.getStatus().getCode(), clientName);
        }
    }

    @Test
    void givenCheckinCertificateWithForgedClaims_whenValidateMembership_thenAllowsByWorkloadIdentity() throws Exception {
        // given
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), "forged-user");
        metadata.put(Metadata.Key.of("x-user-role", Metadata.ASCII_STRING_MARSHALLER), "SUPER_ADMIN");
        MemberServiceGrpc.MemberServiceBlockingStub stub =
                stubWith("client-checkin").withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        // when
        var response = stub.validateMembership(request());

        // then
        assertEquals(memberId, response.getMemberId());
    }

    @Test
    void givenPlaintextClient_whenValidateMembership_thenTransportFails() {
        // given
        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build();
        try {
            MemberServiceGrpc.MemberServiceBlockingStub stub =
                    MemberServiceGrpc.newBlockingStub(channel).withDeadlineAfter(2, TimeUnit.SECONDS);

            // when / then
            assertThrows(Exception.class, () -> stub.validateMembership(request()));
        } finally {
            channel.shutdownNow();
        }
    }

    private ValidateMembershipRequest request() {
        return ValidateMembershipRequest.newBuilder().setUserId(userId).setGymId(gymId).build();
    }

    private MemberServiceGrpc.MemberServiceBlockingStub stubWith(String clientName) throws SSLException {
        File ca = CERT_DIR.resolve("ca.crt").toFile();
        File cert = CERT_DIR.resolve(clientName + ".crt").toFile();
        File key = CERT_DIR.resolve(clientName + ".key").toFile();
        SslContext sslContext = GrpcSslContexts.forClient().trustManager(ca).keyManager(cert, key).build();
        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", grpcPort)
                .sslContext(sslContext)
                .overrideAuthority("localhost")
                .build();
        channels.add(channel);
        return MemberServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
    }
}
