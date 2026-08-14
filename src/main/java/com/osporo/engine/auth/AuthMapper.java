package com.osporo.engine.auth;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.osporo.engine.auth.dto.LoginResponse;
import com.osporo.engine.auth.dto.RegisterResponse;
import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.user.model.User;

@Component
public class AuthMapper {

    public RegisterResponse toRegisterResponse(User user) {
        return new RegisterResponse(
            user.getId(),
            user.getEmail(),
            user.getRoles().stream()
                .map(RoleType::name)
                .collect(Collectors.toList())
        );
    }

    public LoginResponse toLoginResponse(TokenPair tokens) {
        return new LoginResponse(
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.expiresIn()
        );
    }
}
