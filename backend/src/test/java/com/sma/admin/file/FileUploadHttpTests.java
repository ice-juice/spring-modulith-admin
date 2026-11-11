package com.sma.admin.file;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.sma.admin.support.*;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.security.requests.minimum-write-interval=100ms"})
class FileUploadHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Test void uploadKeyRequiredIndependentFilesAllowedAndOwnedLookupNeedsNoBytes()throws Exception{
        var actor=FileUploadRequestTests.actor(79811);String key=UUID.randomUUID().toString();
        var missing=mvc.perform(multipart("/api/file/files").file(FileUploadRequestTests.file("one")).with(Users.request(actor))).andReturn().getResponse();
        assertThat(missing.getStatus()).isEqualTo(400);
        var first=mvc.perform(multipart("/api/file/files").file(FileUploadRequestTests.file("one")).header("Idempotency-Key",key).with(Users.request(actor))).andReturn().getResponse();
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);assertThat(first.getHeader("X-Request-Policy")).isEqualTo("NATIVE");
        assertThat(first.getHeader("X-Upload-Id")).isNotBlank();assertThat(first.getHeader("X-Operation-Id")).isNull();
        String id=json.readTree(first.getContentAsString()).at("/data/id").asText();assertThat(id).isNotBlank();
        var next=mvc.perform(multipart("/api/file/files").file(FileUploadRequestTests.file("two")).header("Idempotency-Key",UUID.randomUUID().toString()).with(Users.request(actor))).andReturn().getResponse();
        assertThat(next.getStatus()).as(next.getContentAsString()).isEqualTo(200);
        var lookup=mvc.perform(post("/api/file/uploads/lookup").contentType("application/json").content("{\"key\":\""+key+"\"}").with(Users.request(actor))).andReturn().getResponse();
        assertThat(lookup.getStatus()).as(lookup.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(lookup.getContentAsString()).at("/data/fileId").asText()).isEqualTo(id);
        assertThat(json.readTree(lookup.getContentAsString()).at("/data/name").isMissingNode()).isTrue();
        Thread.sleep(150);
        var replay=mvc.perform(multipart("/api/file/files").file(FileUploadRequestTests.file("one")).header("Idempotency-Key",key).with(Users.request(actor))).andReturn().getResponse();
        assertThat(replay.getStatus()).as(replay.getContentAsString()).isEqualTo(200);assertThat(json.readTree(replay.getContentAsString()).at("/data/id").asText()).isEqualTo(id);
        var deleted=mvc.perform(delete("/api/file/files/"+id).header("Idempotency-Key",UUID.randomUUID().toString()).with(Users.request(actor))).andReturn().getResponse();
        assertThat(deleted.getStatus()).as(deleted.getContentAsString()).isEqualTo(200);assertThat(deleted.getHeader("X-Operation-Id")).isNotBlank();
    }
    @Test void loggedInImageUploadStillRejectsNonRasterAndForeignOrigin()throws Exception{
        var actor=Users.principal(102,79812,com.sma.admin.common.security.DataAccess.own());
        var rejected=mvc.perform(multipart("/api/file/files/images").file(FileUploadRequestTests.file("not-image")).header("Idempotency-Key",UUID.randomUUID().toString()).with(Users.request(actor))).andReturn().getResponse();
        assertThat(rejected.getStatus()).isEqualTo(200);assertThat(json.readTree(rejected.getContentAsString()).at("/error").asText()).isEqualTo("file.error.invalid-image");
        var foreign=mvc.perform(multipart("/api/file/files/images").file(FileUploadRequestTests.file("one")).header("Origin","https://foreign.invalid").header("Idempotency-Key",UUID.randomUUID().toString()).with(Users.request(actor))).andReturn().getResponse();
        assertThat(foreign.getStatus()).isEqualTo(403);
    }
}
