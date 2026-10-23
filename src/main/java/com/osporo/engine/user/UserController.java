package com.osporo.engine.user;

import com.osporo.engine.shared.response.ApiResponse;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.user.dto.OwnProfileResponse;
import com.osporo.engine.user.dto.UpdateUserProfileRequest;
import com.osporo.engine.user.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<OwnProfileResponse>> getOwnProfile(
            @AuthenticationPrincipal OsporoPrincipal principal
    ) {
        User user = userService.getOwnProfile(principal);
        OwnProfileResponse profile = userMapper.toOwnProfileResponse(user, principal.getPermissions());

        return ResponseEntity.ok(ApiResponse.of(profile));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<OwnProfileResponse>> updateOwnProfile(
            @AuthenticationPrincipal OsporoPrincipal principal,
            @RequestBody UpdateUserProfileRequest request
    ) {
        request.validate();

        User user = userService.updateOwnProfile(principal, request);
        OwnProfileResponse profile = userMapper.toOwnProfileResponse(user, principal.getPermissions());

        return ResponseEntity.ok(ApiResponse.of(profile));
    }
}
