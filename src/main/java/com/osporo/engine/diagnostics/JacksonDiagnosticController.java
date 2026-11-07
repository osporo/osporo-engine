package com.osporo.engine.diagnostics;

import com.osporo.engine.shared.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Profile("jackson-diagnostic")
@RestController
public class JacksonDiagnosticController {

    @PostMapping("/diagnostics/jackson-coercion")
    public ApiResponse<CoercionRequest> echo(@RequestBody CoercionRequest request) {
        return ApiResponse.of(request);
    }

    public record CoercionRequest(String value) {}
}
