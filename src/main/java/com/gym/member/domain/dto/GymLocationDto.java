package com.gym.member.domain.dto;

import java.util.UUID;

public record GymLocationDto(
        UUID id,
        UUID chainId,
        String name,
        String address,
        String city,
        String status
) {}
