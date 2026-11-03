package com.manucore.policy.service;

import java.util.Locale;

/* Inverse of the gateway's method-to-action mapping, used by the permission matrix */
final class ActionMethods {

    private ActionMethods() {}

    static String methodFor(String action) {
        return switch (action.toUpperCase(Locale.ROOT)) {
            case "READ" -> "GET";
            case "UPDATE" -> "PUT";
            case "DELETE" -> "DELETE";
            default -> "POST";
        };
    }
}
