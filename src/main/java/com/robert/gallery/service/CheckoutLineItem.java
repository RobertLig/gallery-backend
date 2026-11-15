package com.robert.gallery.service;

public record CheckoutLineItem(
        String name,
        long amountMinor
) {
}
