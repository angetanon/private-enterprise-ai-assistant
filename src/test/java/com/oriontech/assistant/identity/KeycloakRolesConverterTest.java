package com.oriontech.assistant.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRolesConverterTest {

    private final KeycloakRolesConverter converter = new KeycloakRolesConverter();

    @Test
    void mapsRolesToPrefixedUpperCaseAuthorities() {
        Jwt jwt = jwt().claim("roles", List.of("user", "admin")).build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void returnsNoAuthority_whenRolesClaimIsMissing() {
        assertThat(converter.convert(jwt().build())).isEmpty();
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("u1");
    }
}