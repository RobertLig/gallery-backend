package com.robert.gallery.domain;

public enum CheckoutOrderStatus {
    PENDING_PAYMENT,
    ASYNC_PAYMENT_PENDING,
    PAID,
    EXPIRED,
    FAILED
}
