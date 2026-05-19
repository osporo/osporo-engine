package com.osporo.engine.shared.exception;

import com.osporo.engine.shared.enums.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ListingNotFoundException extends OsporoException {

    public ListingNotFoundException(UUID listingId) {
        super(
                ErrorCode.LISTING_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Listing " + listingId + " was not found."
        );
    }
}