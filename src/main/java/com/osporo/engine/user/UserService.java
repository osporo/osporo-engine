package com.osporo.engine.user;

import com.osporo.engine.user.model.User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService (
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    public User getUser(UUID id) {
        return userRepository.getReferenceById(id);
    }
}
