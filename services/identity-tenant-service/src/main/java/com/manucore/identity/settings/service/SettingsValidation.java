package com.manucore.identity.settings.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;

final class SettingsValidation {

    private SettingsValidation() {
    }

    static String timezone(String value) {
        String zone = value.trim();

        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone: " + zone);
        }

        return zone;
    }

    static String currency(String value) {
        try {
            return Currency.getInstance(value.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException unknown) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown currency: " + value);
        }
    }
}