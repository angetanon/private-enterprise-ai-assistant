package com.oriontech.assistant.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ProbeController.class,
        properties = "app.security.public-docs=false"
)
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/mistral/chat")).andExpect(status().isUnauthorized());
    }

    @Test
    void user_canChat() throws Exception {
        mvc.perform(post("/api/mistral/chat").with(tokenWithRoles("user"))).andExpect(status().isOk());
    }

    @Test
    void adminAlone_canChat() throws Exception {
        mvc.perform(post("/api/mistral/chat").with(tokenWithRoles("admin"))).andExpect(status().isOk());
    }

    @Test
    void user_cannotUploadDocuments() throws Exception {
        mvc.perform(post("/api/documents").with(tokenWithRoles("user"))).andExpect(status().isForbidden());
    }

    @Test
    void admin_canUploadDocuments() throws Exception {
        mvc.perform(post("/api/documents").with(tokenWithRoles("user", "admin"))).andExpect(status().isOk());
    }

    @Test
    void tokenWithoutRoles_isForbidden() throws Exception {
        mvc.perform(post("/api/mistral/chat").with(tokenWithRoles())).andExpect(status().isForbidden());
    }

    @Test
    void unknownPath_isDenied() throws Exception {
        mvc.perform(get("/other").with(tokenWithRoles("admin"))).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor tokenWithRoles(String... roles) {
        return jwt()
                .jwt(j -> j.subject("u1").claim("roles", List.of(roles)))
                .authorities(new KeycloakRolesConverter());   // goes through your converter
    }
}