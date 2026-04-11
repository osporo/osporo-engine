package com.osporo.engine.auth;

import com.osporo.engine.auth.dto.RegisterRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("v1/auth")
public class AuthController {

    public AuthController() {}

    @GetMapping("register")
    public String register() {
        return "Register endpoint";
    }

    @PostMapping("register")
    public ResponseEntity<RegisterRequest> register(
            @RequestBody RegisterRequest registerRequest
    ) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build("");
        return ResponseEntity.created(location).body(registerRequest);
    }



}
