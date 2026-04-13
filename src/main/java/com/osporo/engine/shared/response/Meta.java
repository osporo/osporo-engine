package com.osporo.engine.shared.response;

import org.springframework.data.domain.Page;
import java.util.UUID;

public record Meta(String requestId, PaginationMeta pagination) {

    public static Meta simple() {
        return new Meta(UUID.randomUUID().toString(), null);
    }

    public static Meta paginated(Page<?> page) {
        return new Meta(
                UUID.randomUUID().toString(),
                new PaginationMeta(
                        page.getNumber() + 1,
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages()
                )
        );
    }
}
