package com.gym.member.mapper;

import org.mapstruct.Named;

import java.time.LocalDate;
import java.util.UUID;

public class CommonMapperUtils {

    private CommonMapperUtils() {}

    @Named("toUuid")
    public static UUID toUuid(String str) {
        return (str != null && !str.isBlank()) ? UUID.fromString(str) : null;
    }

    @Named("uuidToString")
    public static String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : "";
    }

    @Named("dateToString")
    public static String dateToString(LocalDate date) {
        return date != null ? date.toString() : "";
    }
}
