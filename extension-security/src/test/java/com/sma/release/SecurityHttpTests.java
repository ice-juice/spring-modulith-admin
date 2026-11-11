package com.sma.release;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:release-http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa","spring.datasource.password=",
        "release.directory=target/http-test-storage","release.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "release.origin=https://release.example.test","release.bootstrap-file="})
@AutoConfigureMockMvc
class SecurityHttpTests {
    @Autowired MockMvc mvc;
    @Test void controlRoutesRequireASecurityServiceSessionAndNeverCacheResponses() throws Exception {
        mvc.perform(get("/release/v1/groups").header("Authorization","Bearer " + "a".repeat(43)))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void browserOriginsAndDuplicateJsonAreRejectedBeforeAuthentication() throws Exception {
        mvc.perform(post("/release/v1/auth/login").header("Origin","https://attacker.example")
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("extension.origin-denied"));
        mvc.perform(post("/release/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"first\",\"username\":\"second\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("extension.invalid-document"));
    }
    @Test void publicUnknownRoutesAndMachineTokenConfusionFailClosed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
        mvc.perform(post("/release/v1/machines/workers/claim").param("environment","production")
                .header("Authorization","Bearer " + "a".repeat(43))).andExpect(status().isUnauthorized());
    }
}
