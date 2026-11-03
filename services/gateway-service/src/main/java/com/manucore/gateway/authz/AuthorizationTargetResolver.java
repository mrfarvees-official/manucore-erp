package com.manucore.gateway.authz;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Maps an HTTP request to the (action, resource) pair checked by the authorization service.
 *
 *   /api/v1/{service}/{collection}[/{id}[/{verb}]]
 *
 *   resource = SERVICE.COLLECTION       (upper-cased, '-' becomes '_')
 *   GET/HEAD                  -> READ
 *   POST   .../{collection}   -> CREATE
 *   POST   .../{id}/{verb}    -> VERB   (e.g. ADJUST)
 *   PUT/PATCH                 -> UPDATE
 *   DELETE                    -> DELETE
 *
 * Anything else, including odd characters, returns empty, and the gateway denies it.
 */
public final class AuthorizationTargetResolver {

    public record Target(String action, String resource) {
    }

    private static final String PREFIX = "/api/v1/";
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private AuthorizationTargetResolver() {
    }

    public static Optional<Target> resolve(String method, String rawPath) {

        if (method == null || rawPath == null || !rawPath.startsWith(PREFIX)) {
            return Optional.empty();
        }

        String rest = rawPath.substring(PREFIX.length());
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }

        String[] segments = rest.split("/", -1);
        if (segments.length < 2) {
            return Optional.empty();
        }

        for (String segment : segments) {
            if (!SEGMENT.matcher(segment).matches()) {
                return Optional.empty();
            }
        }

        String resource = normalize(segments[0]) + "." + normalize(segments[1]);

        String action = switch (method.toUpperCase(Locale.ROOT)) {
            case "GET", "HEAD" -> "READ";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            case "POST" -> switch (segments.length) {
                case 2 -> "CREATE";
                case 4 -> normalize(segments[3]);
                default -> null;
            };
            default -> null;
        };

        return action == null
                ? Optional.empty()
                : Optional.of(new Target(action, resource));
    }

    private static String normalize(String segment) {
        return segment.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}