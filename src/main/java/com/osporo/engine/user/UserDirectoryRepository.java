package com.osporo.engine.user;

import com.osporo.engine.user.dto.UserDirectoryQuery;
import com.osporo.engine.user.model.User;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface UserDirectoryRepository {

    Page<User> findDirectory(UUID tenantId, UserDirectoryQuery filters);
}
