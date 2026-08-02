package com.gym.member.location.adapter.in.grpc;

import com.gym.common.grpc.security.RequireRole;
import com.gym.member.location.application.port.in.GymLocationUseCase;
import com.gym.member.location.application.port.in.GymQRUseCase;
import com.gym.member.location.domain.dto.GymDailySecretDto;
import com.gym.member.location.domain.dto.GymLocationDto;
import com.gym.member.member.domain.dto.PlanDto;
import com.gym.member.location.adapter.out.persistence.mapper.GymLocationMapper;
import com.gym.member.location.adapter.out.persistence.mapper.GymQRSecretMapper;
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
import static com.gym.member.adapter.in.grpc.GrpcErrorHandler.execute;

@Component
@RequiredArgsConstructor
public class GymLocationGrpcDelegate {

    private final GymLocationUseCase gymLocationUseCase;
    private final GymQRUseCase gymQRUseCase;
    private final GymLocationMapper gymLocationMapper;
    private final GymQRSecretMapper gymQRSecretMapper;

    @RequireRole({"CUSTOMER", "ADMIN", "SUPER_ADMIN"})
    public void getPlans(GetPlansRequest request, StreamObserver<PlansResponse> responseObserver) {
        execute(responseObserver, () -> {
            if (!request.getGymId().isBlank()) {
                requireGym(request.getGymId());
            }
            List<PlanDto> plans = gymLocationUseCase.getPlans(request.getGymId());
            List<Plan> planProtos = plans.stream().map(gymLocationMapper::toPlanProto).toList();

            return PlansResponse.newBuilder().addAllPlans(planProtos).build();
        });
    }

    @RequireRole("SUPER_ADMIN")
    public void createGymLocation(CreateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        execute(responseObserver, () -> {
            GymLocationDto dto = gymLocationUseCase.createGymLocation(
                    request.getChainId(),
                    request.getName(),
                    request.getAddress(),
                    request.getCity()
            );
            return gymLocationMapper.toResponse(dto);
        });
    }

    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void updateGymLocation(UpdateGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireGym(gymLocationUseCase.getGymLocation(request.getId()).id().toString());
            GymLocationDto dto = gymLocationUseCase.updateGymLocation(
                    request.getId(),
                    request.getName(),
                    request.getAddress(),
                    request.getCity(),
                    request.getStatus()
            );
            return gymLocationMapper.toResponse(dto);
        });
    }

    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void listGymLocations(ListGymLocationsRequest request, StreamObserver<GymLocationsResponse> responseObserver) {
        execute(responseObserver, () -> {
            List<GymLocationDto> dtos = gymLocationUseCase.listGymLocations(request.getChainId());
            List<GymLocationResponse> responses = dtos.stream().map(gymLocationMapper::toResponse).toList();
            return GymLocationsResponse.newBuilder().addAllLocations(responses).build();
        });
    }

    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public void getGymLocation(GetGymLocationRequest request, StreamObserver<GymLocationResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireGym(request.getId());
            GymLocationDto dto = gymLocationUseCase.getGymLocation(request.getId());
            return gymLocationMapper.toResponse(dto);
        });
    }

    @RequireRole("CHECKIN_SERVICE")
    public void getGymDailySecret(GetGymDailySecretRequest request, StreamObserver<GymDailySecretResponse> responseObserver) {
        execute(responseObserver, () -> {
            requireServiceGym(request.getGymId());
            GymDailySecretDto dto = gymQRUseCase.getGymDailySecret(request.getGymId());
            return gymQRSecretMapper.toResponse(dto);
        });
    }
}
