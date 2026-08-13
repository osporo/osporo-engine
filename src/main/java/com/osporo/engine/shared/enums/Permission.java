package com.osporo.engine.shared.enums;

public enum Permission {

    // Listing
    LISTING_CREATE,
    LISTING_READ,
    LISTING_UPDATE_OWN,
    LISTING_DELETE_OWN,
    LISTING_TAKEDOWN_ANY,

    // Category
    CATEGORY_CREATE,
    CATEGORY_EDIT,
    CATEGORY_DELETE,

    // Order
    ORDER_CREATE,
    ORDER_READ_OWN,
    ORDER_READ_ANY,
    ORDER_REFUND,

    // Message
    MESSAGE_CREATE,
    MESSAGE_READ_OWN,

    // Moderation
    MODERATION_REVIEW,
    MODERATION_CONFIG,
    REPORT_CREATE,

    // User
    USER_READ_ANY,
    USER_SUSPEND,
    USER_INVITE,

    // Platform
    PLATFORM_ADMIN
}
