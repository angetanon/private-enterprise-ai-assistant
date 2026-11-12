package com.oriontech.assistant.identity;

import java.util.Set;

public record CurrentUser(String id, String username, Set<String> roles) {

    public boolean isAdmin() {
        return roles.contains("admin");
    }
}