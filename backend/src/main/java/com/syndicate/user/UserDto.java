package com.syndicate.user;

import java.util.UUID;

public record UserDto(UUID id, String email, String fullName) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName());
    }
}
