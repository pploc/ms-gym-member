package com.gym.member.adapter.in.grpc;

import com.gym.common.grpc.security.RequireRole;
import com.gym.member.application.service.GymLocationService;
import com.gym.member.application.service.GymQRService;
import com.gym.member.domain.dto.GymDailySecretDto;
import com.gym.member.domain.dto.GymLocationDto;
import com.gym.member.domain.dto.PlanDto;
import com.gym.member.mapper.GymLocationMapper;
import com.gym.member.mapper.GymQRSecretMapper;
import com.gym.proto.member.v1.CreateGymLocationRequest;
import com.gym.proto.member.v1.GetGymDailySecretRequest;
import com.gym.proto.member.v1.GetGymLocationRequest;
import com.gym.proto.member.v1.GetPlansRequest;
import com.gym.proto.member.v1.GymDailySecretResponse;
import com.gym.proto.member.v1.GymLocationResponse;
import com.gym.proto.member.v1.GymLocationsResponse;
import com.gym.proto.member.v1.ListGymLocationsRequest;
import com.gym.proto.member.v1.Plan;
import com.gym.proto.member.v1.PlansResponse;
import com.gym.proto.member.v1.UpdateGymLocationRequest;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireGym;
import static com.gym.member.adapter.in.grpc.GrpcAccessPolicy.requireServiceGym;
import static com.gym.member.adapter.in.grpc.GrpcErrorHandler.handleError;

@Component
@RequiredArgsConstructor
public class GymLocationGrpcDelegate {

    private final GymLocationService gymLocationService;
    private final GymQRService gymQRService;
    private final GymLocationMapper gymLocationMapper;
    private final GymQRSecretMapper gymQRSecretMapper;

    @RequireRole({"CUSTOMER", "ADMIN", "SUPER_ADMIN"})
    public void getPlans(GetPlansRequest request, StreamObserver<PlansResponse> responseObserver) {
        try {
            if (!request.getGymId().isBlank()) {
                requireGym(request.getGymId());
            }
            List<PlanDto> plans = gymLocationService.getPlans(request.getGymId());
            List<Plan> planProtos = plans.stream().map(gymLocationMapper::toPlanProto).toList();

            responseObserver.onNext(PlansResponse.newBuilder().addAllPlans(planProtos).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole("SUPER_ADMIN")
    public void createGymLocation(CreateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        try {
            GymLocationDto dto = gymLocationService.createGymLocation(
                    request.getChainId(),
                    request.getName(),
                    request.getAddress(),
                    request.getCity()
            );
            responseObserver.onNext(gymLocationMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void updateGymLocation(UpdateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        try {
            requireGym(gymLocationService.getGymLocation(request.getId()).id().toString());
            GymLocationDto dto = gymLocationService.updateGymLocation(
                    request.getId(),
                    request.getName(),
                    request.getAddress(),
                    request.getCity(),
                    request.getStatus()
            );
            responseObserver.onNext(gymLocationMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void listGymLocations(ListGymLocationsRequest request, StreamObserver<GymLocationsResponse> responseObserver) {
        try {
            List<GymLocationDto> dtos = gymLocationService.listGymLocations(request.getChainId());
            List<GymLocationResponse> responses = dtos.stream().map(gymLocationMapper::toResponse).toList();
            responseObserver.onNext(GymLocationsResponse.newBuilder().addAllLocations(responses).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void getGymLocation(GetGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        try {
            requireGym(request.getId());
            GymLocationDto dto = gymLocationService.getGymLocation(request.getId());
            responseObserver.onNext(gymLocationMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    @RequireRole("CHECKIN_SERVICE")
    public void getGymDailySecret(GetGymDailySecretRequest request, StreamObserver<GymDailySecretResponse> responseObserver) {
        try {
            requireServiceGym(request.getGymId());
            GymDailySecretDto dto = gymQRService.getGymDailySecret(request.getGymId());
            responseObserver.onNext(gymQRSecretMapper.toResponse(dto));
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }
}
