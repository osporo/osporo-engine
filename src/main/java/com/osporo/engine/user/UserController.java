package com.osporo.engine.user;

import com.osporo.engine.shared.response.ApiResponse;
import com.osporo.engine.shared.response.ErrorEnvelope;
import com.osporo.engine.shared.response.Meta;
import com.osporo.engine.shared.enums.ErrorCode;
import com.osporo.engine.shared.security.OsporoPrincipal;
import com.osporo.engine.user.dto.OwnProfileResponse;
import com.osporo.engine.user.dto.UpdateUserProfileRequest;
import com.osporo.engine.user.dto.UserDirectoryQuery;
import com.osporo.engine.user.dto.UserSummaryResponse;
import com.osporo.engine.user.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final UserAuthorizationPolicy userAuthorizationPolicy;

    public UserController(
            UserService userService,
            UserMapper userMapper,
            UserAuthorizationPolicy userAuthorizationPolicy
    ) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.userAuthorizationPolicy = userAuthorizationPolicy;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ_ANY')")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getUserDirectory(
            @AuthenticationPrincipal OsporoPrincipal principal,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        UserDirectoryQuery filters = UserDirectoryQuery.from(parameters);
        Page<UserSummaryResponse> users = userService.getUserDirectory(principal, filters)
                .map(userMapper::toSummaryResponse);

        return ResponseEntity.ok(ApiResponse.of(users.getContent(), Meta.paginated(users)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getUserProfile(
            @PathVariable UUID id,
            @AuthenticationPrincipal OsporoPrincipal principal
    ) {
        User user = userService.getUserProfile(id, principal);

        if (userAuthorizationPolicy.canReadFullProfile(principal)) {
            return ResponseEntity.ok(ApiResponse.of(userMapper.toFullProfileResponse(user)));
        }

        return ResponseEntity.ok(ApiResponse.of(userMapper.toPublicProfileResponse(user)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of(
                        ErrorCode.INVALID_PARAMETER,
                        "The " + exception.getName() + " parameter has an invalid value."
                ));
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
