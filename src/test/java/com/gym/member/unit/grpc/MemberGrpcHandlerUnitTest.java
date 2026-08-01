package com.gym.member.unit.grpc;

import com.gym.common.error.NotFoundException;
import com.gym.common.pagination.NormalPage;
import com.gym.member.adapter.in.grpc.MemberGrpcHandler;
import com.gym.member.application.service.GymLocationService;
import com.gym.member.application.service.GymQRService;
import com.gym.member.application.service.MemberService;
import com.gym.member.application.service.SubscriptionService;
import com.gym.member.domain.dto.GymDailySecretDto;
import com.gym.member.domain.dto.GymLocationDto;
import com.gym.member.domain.dto.MemberDto;
import com.gym.member.domain.dto.PlanDto;
import com.gym.member.domain.dto.SubscriptionDto;
import com.gym.member.domain.exception.CannotPauseLifetimeException;
import com.gym.member.domain.model.MembershipStatus;
import com.gym.member.domain.model.PlanType;
import com.gym.member.mapper.GymLocationMapper;
import com.gym.member.mapper.GymQRSecretMapper;
import com.gym.member.mapper.MemberMapper;
import com.gym.member.mapper.SubscriptionMapper;
import com.gym.proto.member.v1.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberGrpcHandlerUnitTest {

    @Mock
    private MemberService memberService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private GymLocationService gymLocationService;

    @Mock
    private GymQRService gymQRService;

    @Mock
    private StreamObserver responseObserver;

    @Spy
    private MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @Spy
    private SubscriptionMapper subscriptionMapper = Mappers.getMapper(SubscriptionMapper.class);

    @Spy
    private GymLocationMapper gymLocationMapper = Mappers.getMapper(GymLocationMapper.class);

    @Spy
    private GymQRSecretMapper gymQRSecretMapper = Mappers.getMapper(GymQRSecretMapper.class);

    @InjectMocks
    private MemberGrpcHandler memberGrpcHandler;

    private UUID memberId;
    private UUID userId;
    private UUID gymId;
    private UUID chainId;
    private MemberDto memberDto;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        userId = UUID.randomUUID();
        gymId = UUID.randomUUID();
        chainId = UUID.randomUUID();

        memberDto = new MemberDto(memberId, userId, gymId, "John Doe", "123456", "http://avatar", LocalDate.of(1995, 5, 15), MembershipStatus.ACTIVE, null, null);
    }

    @Test
    void givenValidMemberId_whenGetMember_thenReturnsMemberResponse() {
        // Given
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        // When
        memberGrpcHandler.getMember(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(MemberResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenNotFoundException_whenGetMember_thenCallsOnError() {
        // Given
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new NotFoundException("Not found"));

        // When
        memberGrpcHandler.getMember(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenIllegalArgumentException_whenGetMember_thenCallsOnError() {
        // Given
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new IllegalArgumentException("Invalid argument"));

        // When
        memberGrpcHandler.getMember(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenDomainException_whenGetMember_thenCallsOnError() {
        // Given
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new CannotPauseLifetimeException("Precondition failed"));

        // When
        memberGrpcHandler.getMember(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenRuntimeException_whenGetMember_thenCallsOnError() {
        // Given
        GetMemberRequest request = GetMemberRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenThrow(new RuntimeException("Internal error"));

        // When
        memberGrpcHandler.getMember(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidUpdateProfileRequest_whenUpdateProfile_thenReturnsMemberResponse() {
        // Given
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder().setMemberId(memberId.toString()).setFullName("Jane").build();
        when(memberService.updateProfile(eq(memberId.toString()), eq("Jane"), any(), any(), any())).thenReturn(memberDto);

        // When
        memberGrpcHandler.updateProfile(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(MemberResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnUpdateProfile_whenUpdateProfile_thenCallsOnError() {
        // Given
        UpdateProfileRequest request = UpdateProfileRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.updateProfile(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.updateProfile(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidListMembersRequest_whenListMembers_thenReturnsListMembersResponse() {
        // Given
        ListMembersRequest request = ListMembersRequest.newBuilder().setGymId(gymId.toString()).setPage(0).setLimit(10).build();
        NormalPage<MemberDto> normalPage = new NormalPage<>(List.of(memberDto), 0, 10, 1L, 1);
        when(memberService.listMembers(gymId.toString(), 0, 10)).thenReturn(normalPage);

        // When
        memberGrpcHandler.listMembers(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(ListMembersResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnListMembers_whenListMembers_thenCallsOnError() {
        // Given
        ListMembersRequest request = ListMembersRequest.newBuilder().build();
        when(memberService.listMembers(any(), anyInt(), anyInt())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.listMembers(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidGymId_whenGetPlans_thenReturnsPlansResponse() {
        // Given
        GetPlansRequest request = GetPlansRequest.newBuilder().setGymId(gymId.toString()).build();
        PlanDto plan = new PlanDto(UUID.randomUUID(), gymId, "Monthly Pass", PlanType.MONTHLY, 30, 500000L, "Desc", true);
        when(gymLocationService.getPlans(gymId.toString())).thenReturn(List.of(plan));

        // When
        memberGrpcHandler.getPlans(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(PlansResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetPlans_whenGetPlans_thenCallsOnError() {
        // Given
        GetPlansRequest request = GetPlansRequest.newBuilder().build();
        when(gymLocationService.getPlans(any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.getPlans(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenPurchaseRequest_whenPurchaseMembership_thenReturnsPurchaseResponse() {
        // Given
        PurchaseMembershipRequest request = PurchaseMembershipRequest.newBuilder().setPlanId(UUID.randomUUID().toString()).build();

        // When
        memberGrpcHandler.purchaseMembership(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(PurchaseResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenValidMemberId_whenPauseMembership_thenReturnsMembershipResponse() {
        // Given
        PauseMembershipRequest request = PauseMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.PAUSED, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(subscriptionService.pauseSubscription(memberId.toString())).thenReturn(subDto);

        // When
        memberGrpcHandler.pauseMembership(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnPauseMembership_whenPauseMembership_thenCallsOnError() {
        // Given
        PauseMembershipRequest request = PauseMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(subscriptionService.pauseSubscription(any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.pauseMembership(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidMemberId_whenResumeMembership_thenReturnsMembershipResponse() {
        // Given
        ResumeMembershipRequest request = ResumeMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(subscriptionService.resumeSubscription(memberId.toString())).thenReturn(subDto);

        // When
        memberGrpcHandler.resumeMembership(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnResumeMembership_whenResumeMembership_thenCallsOnError() {
        // Given
        ResumeMembershipRequest request = ResumeMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(subscriptionService.resumeSubscription(any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.resumeMembership(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidMemberId_whenGetMembershipStatus_thenReturnsMembershipResponse() {
        // Given
        GetMembershipStatusRequest request = GetMembershipStatusRequest.newBuilder().setMemberId(memberId.toString()).build();
        SubscriptionDto subDto = new SubscriptionDto(UUID.randomUUID(), memberId, UUID.randomUUID(), MembershipStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), null, 30, 1);
        when(subscriptionService.getActiveSubscription(memberId.toString())).thenReturn(subDto);

        // When
        memberGrpcHandler.getMembershipStatus(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(MembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetMembershipStatus_whenGetMembershipStatus_thenCallsOnError() {
        // Given
        GetMembershipStatusRequest request = GetMembershipStatusRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(subscriptionService.getActiveSubscription(any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.getMembershipStatus(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidCreateGymLocationRequest_whenCreateGymLocation_thenReturnsGymLocationResponse() {
        // Given
        CreateGymLocationRequest request = CreateGymLocationRequest.newBuilder().setChainId(chainId.toString()).setName("Gym A").setAddress("Addr").setCity("City").build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym A", "Addr", "City", "ACTIVE");
        when(gymLocationService.createGymLocation(chainId.toString(), "Gym A", "Addr", "City")).thenReturn(locDto);

        // When
        memberGrpcHandler.createGymLocation(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnCreateGymLocation_whenCreateGymLocation_thenCallsOnError() {
        // Given
        CreateGymLocationRequest request = CreateGymLocationRequest.newBuilder().build();
        when(gymLocationService.createGymLocation(any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.createGymLocation(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidUpdateGymLocationRequest_whenUpdateGymLocation_thenReturnsGymLocationResponse() {
        // Given
        UpdateGymLocationRequest request = UpdateGymLocationRequest.newBuilder().setId(gymId.toString()).setName("Gym B").build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.updateGymLocation(eq(gymId.toString()), eq("Gym B"), any(), any(), any())).thenReturn(locDto);

        // When
        memberGrpcHandler.updateGymLocation(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnUpdateGymLocation_whenUpdateGymLocation_thenCallsOnError() {
        // Given
        UpdateGymLocationRequest request = UpdateGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        when(gymLocationService.updateGymLocation(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.updateGymLocation(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenChainId_whenListGymLocations_thenReturnsGymLocationsResponse() {
        // Given
        ListGymLocationsRequest request = ListGymLocationsRequest.newBuilder().setChainId(chainId.toString()).build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.listGymLocations(chainId.toString())).thenReturn(List.of(locDto));

        // When
        memberGrpcHandler.listGymLocations(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(GymLocationsResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnListGymLocations_whenListGymLocations_thenCallsOnError() {
        // Given
        ListGymLocationsRequest request = ListGymLocationsRequest.newBuilder().build();
        when(gymLocationService.listGymLocations(any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.listGymLocations(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenGymId_whenGetGymLocation_thenReturnsGymLocationResponse() {
        // Given
        GetGymLocationRequest request = GetGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        GymLocationDto locDto = new GymLocationDto(gymId, chainId, "Gym B", "Addr", "City", "ACTIVE");
        when(gymLocationService.getGymLocation(gymId.toString())).thenReturn(locDto);

        // When
        memberGrpcHandler.getGymLocation(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(GymLocationResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetGymLocation_whenGetGymLocation_thenCallsOnError() {
        // Given
        GetGymLocationRequest request = GetGymLocationRequest.newBuilder().setId(gymId.toString()).build();
        when(gymLocationService.getGymLocation(any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.getGymLocation(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenValidMember_whenValidateMembership_thenReturnsValidateMembershipResponse() {
        // Given
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder().setMemberId(memberId.toString()).setGymId(gymId.toString()).build();
        when(memberService.getMember(memberId.toString())).thenReturn(memberDto);

        // When
        memberGrpcHandler.validateMembership(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(ValidateMembershipResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnValidateMembership_whenValidateMembership_thenCallsOnError() {
        // Given
        ValidateMembershipRequest request = ValidateMembershipRequest.newBuilder().setMemberId(memberId.toString()).build();
        when(memberService.getMember(any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.validateMembership(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenGymId_whenGetGymDailySecret_thenReturnsGymDailySecretResponse() {
        // Given
        GetGymDailySecretRequest request = GetGymDailySecretRequest.newBuilder().setGymId(gymId.toString()).build();
        GymDailySecretDto secretDto = new GymDailySecretDto(gymId, "secret-xyz");
        when(gymQRService.getGymDailySecret(gymId.toString())).thenReturn(secretDto);

        // When
        memberGrpcHandler.getGymDailySecret(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(GymDailySecretResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnGetGymDailySecret_whenGetGymDailySecret_thenCallsOnError() {
        // Given
        GetGymDailySecretRequest request = GetGymDailySecretRequest.newBuilder().setGymId(gymId.toString()).build();
        when(gymQRService.getGymDailySecret(any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.getGymDailySecret(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }

    @Test
    void givenStatus_whenListMembersByStatus_thenReturnsListMembersByStatusResponse() {
        // Given
        ListMembersByStatusRequest request = ListMembersByStatusRequest.newBuilder().setStatus("ACTIVE").build();
        when(memberService.listMembersByStatus(MembershipStatus.ACTIVE, List.of())).thenReturn(List.of(memberDto));

        // When
        memberGrpcHandler.listMembersByStatus(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onNext(any(ListMembersByStatusResponse.class));
        verify(responseObserver, times(1)).onCompleted();
    }

    @Test
    void givenErrorOnListMembersByStatus_whenListMembersByStatus_thenCallsOnError() {
        // Given
        ListMembersByStatusRequest request = ListMembersByStatusRequest.newBuilder().setStatus("ACTIVE").build();
        when(memberService.listMembersByStatus(any(), any())).thenThrow(new RuntimeException("Error"));

        // When
        memberGrpcHandler.listMembersByStatus(request, responseObserver);

        // Then
        verify(responseObserver, times(1)).onError(any());
    }
}
