package com.qwikride.dto;

import com.qwikride.model.User;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoleToggleRequestDTO {
    @NotNull(message = "Role is required")
    private User.UserRole role;
}

