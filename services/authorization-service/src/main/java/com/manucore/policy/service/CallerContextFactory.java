package com.manucore.policy.service;

import com.manucore.policy.dto.CallerContext;
import com.manucore.policy.enums.ConditionAttribute;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CallerContextFactory {

    public static final String CLIENT_IP_HEADER = "X-Client-Ip";
    public static final String ORIGINAL_METHOD_HEADER = "X-Original-Method";

    private final ZoneId zone;

    public CallerContextFactory(@Value("${manucore.authorization.zone:UTC}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public CallerContext from(Jwt jwt, HttpServletRequest request) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        String userId = jwt.getSubject();

        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Required identity claims are missing");

        List<String> roles = jwt.getClaimAsStringList("roles");

        ZonedDateTime now = ZonedDateTime.now(zone);

        Map<ConditionAttribute, Object> attributes = new EnumMap<>(ConditionAttribute.class);
        attributes.put(ConditionAttribute.IP_ADDRESS, clientIp(request));
        attributes.put(ConditionAttribute.TIME, now.toLocalTime());
        attributes.put(ConditionAttribute.DAY_OF_WEEK, now.getDayOfWeek());

        return new CallerContext(
                tenantId,
                userId,
                roles == null ? List.of() : roles,
                jwt.getClaimAsString("department_id"),
                jwt.getClaimAsString("service_id"),
                attributes);
    }

    /* The gateway sets these headers (after stripping client-supplied once). */
    public String requestMethod(HttpServletRequest request) {
        String forwarded = request.getHeader(ORIGINAL_METHOD_HEADER);
        return forwarded != null && forwarded.matches("[A-Z]{3,10}")
                ? forwarded
                : request.getMethod();
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader(CLIENT_IP_HEADER);
        return header != null && header.matches("[0-9a-fA-F:.]{2,45}")
                ? header
                : request.getRemoteAddr();
    }
}
