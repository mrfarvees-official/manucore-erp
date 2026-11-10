package com.manucore.identity.audit;

import com.manucore.identity.account.UserAccount;
import com.manucore.identity.account.repository.UserAccountRepository;
import com.manucore.identity.audit.dto.Actor;
import lombok.AllArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@AllArgsConstructor
public class ActorResolver {

    private final UserAccountRepository users;

    public Actor from(Jwt jwt) {
        String name = users.findById(UUID.fromString(jwt.getSubject()))
                .map(UserAccount::getName)
                .orElse(null);

        return new Actor(jwt.getSubject(), name);
    }
}