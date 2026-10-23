package com.osporo.engine.user;

import com.osporo.engine.shared.enums.RoleType;
import com.osporo.engine.user.dto.OwnProfileResponse;
import com.osporo.engine.user.dto.PublicUserProfileResponse;
import com.osporo.engine.user.dto.FullUserProfileResponse;
import com.osporo.engine.user.model.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public PublicUserProfileResponse toPublicProfileResponse(User user) {
        return new PublicUserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getCreatedAt(),
                null
        );
    }

    public FullUserProfileResponse toFullProfileResponse(User user) {
        return new FullUserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRoles().stream().map(RoleType::name).toList(),
                user.isSuspended() ? "SUSPENDED" : "ACTIVE",
                null,
                user.getCreatedAt(),
                user.getSuspendedAt()
        );
    }

    public OwnProfileResponse toOwnProfileResponse(User user, List<String> permissions) {
        return new OwnProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRoles().stream().map(RoleType::name).toList(),
                permissions
        );
    }
}
