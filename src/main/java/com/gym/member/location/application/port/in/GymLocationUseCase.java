package com.gym.member.location.application.port.in;

import com.gym.member.location.domain.dto.GymLocationDto;
import com.gym.member.member.domain.dto.PlanDto;

import java.util.List;

public interface GymLocationUseCase {
    GymLocationDto createGymLocation(String chainId, String name, String address, String city);
    GymLocationDto updateGymLocation(String id, String name, String address, String city, String status);
    GymLocationDto getGymLocation(String id);
    List<GymLocationDto> listGymLocations(String chainId);
    List<PlanDto> getPlans(String gymId);
}
