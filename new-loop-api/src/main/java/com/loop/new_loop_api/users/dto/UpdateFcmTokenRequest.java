package com.loop.new_loop_api.users.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateFcmTokenRequest {

    @NotBlank(message = "FCM token is required")
    private String fcmToken;
}
