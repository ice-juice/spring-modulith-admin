package com.sma.admin.mail.internal.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sma.admin.common.BizException;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.PageResult;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.common.security.TenantConfigurationRef;
import com.sma.admin.common.security.TenantSecretCodec;
import com.sma.admin.mail.MailErrorCode;
import com.sma.admin.mail.internal.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class MailProfileService {
    public enum TlsMode { STARTTLS, TLS }

    public record Input(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9.-]{0,63}") String code,
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 253) String host,
            @Min(1) @Max(65535) int port,
            @NotNull TlsMode tlsMode,
            @NotBlank @Size(max = 256) String username,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 8192) char[] password,
            @NotBlank @Email @Size(max = 254) String fromAddress,
            @NotBlank @Size(max = 128) String fromName,
            @NotEmpty @Size(max = 100) Set<@NotNull @Pattern(regexp = "[a-z][a-z0-9.-]{0,63}") String> applications,
            @Min(1) @Max(1000000) int dailyQuota,
            @Min(1) @Max(10000) int perMinuteLimit,
            boolean enabled) {
        @Override public String toString() { return "MailProfileInput[REDACTED]"; }
    }

    public record View(Long id, String code, String name, boolean enabled, Long version, Long configurationVersion,
            Long credentialVersion, String host, int port, TlsMode tlsMode, String username,
            boolean passwordConfigured, String fromAddress, String fromName, List<String> applications,
            int dailyQuota, int perMinuteLimit) {}

    private final MailProfileMapper profiles;
    private final MailProfileRevisionMapper revisions;
    private final TenantSecretCodec secrets;
    private final JsonMapper json;
    private final Validator validator;

    public MailProfileService(MailProfileMapper profiles, MailProfileRevisionMapper revisions,
            TenantSecretCodec secrets, JsonMapper json, Validator validator) {
        this.profiles = profiles; this.revisions = revisions; this.secrets = secrets; this.json = json; this.validator = validator;
    }

    @RequiresPermission("mail:profile:view")
    public PageResult<View> page(PageQuery page, String keyword) {
        ActorContext.tenantId();
        var query = new LambdaQueryWrapper<MailProfileEntity>().orderByDesc(MailProfileEntity::getCreatedAt).orderByDesc(MailProfileEntity::getId);
        if (keyword != null && !keyword.isBlank()) query.like(MailProfileEntity::getName, keyword);
        return PageResult.of(profiles.selectPage(page.toPage(Map.of("name", "name", "createdAt", "created_at")), query), this::view);
    }

    @RequiresPermission("mail:profile:view")
    public View get(long id) { return view(owned(id, false)); }

    @RequiresPermission("mail:profile:manage") @Transactional
    public View create(@Valid Input input) {
        validate(input);
        if (input.password() == null || input.password().length == 0) throw invalid();
        var row = new MailProfileEntity();
        row.setId(IdWorker.getId()); row.setTenantId(ActorContext.tenantId());
        row.setCode(input.code()); row.setName(input.name().trim()); row.setEnabled(input.enabled());
        row.setVersion(1L);
        row.setConfigurationVersion(1L); row.setCredentialVersion(1L);
        var revision = revision(row, input, input.password());
        try { profiles.insert(row); revisions.insert(revision); }
        catch (DuplicateKeyException duplicate) { throw new BizException(MailErrorCode.DUPLICATE_CODE); }
        return view(row, revision);
    }

    @RequiresPermission("mail:profile:manage") @Transactional
    public View update(long id, long expectedVersion, @Valid Input input) {
        validate(input);
        var row = owned(id, true);
        checkVersion(row, expectedVersion);
        if (!row.getCode().equals(input.code())) throw invalid(); // Stable binding code, not a mutable display label.
        var old = revision(row);
        var oldReference = reference(row);
        row.setName(input.name().trim()); row.setEnabled(input.enabled());
        row.setVersion(Math.addExact(row.getVersion(), 1));
        row.setConfigurationVersion(Math.addExact(row.getConfigurationVersion(), 1));
        boolean replacing = input.password() != null && input.password().length != 0;
        MailProfileRevisionEntity next;
        if (replacing) {
            row.setCredentialVersion(Math.addExact(row.getCredentialVersion(), 1));
            next = revision(row, input, input.password());
        } else {
            try (var secret = secrets.decrypt(oldReference, "smtp-password", old.getSecretEnvelope())) {
                char[] plaintext = secret.copy();
                try { next = revision(row, input, plaintext); }
                finally { Arrays.fill(plaintext, '\0'); }
            }
        }
        revisions.insert(next); profiles.updateById(row);
        return view(row, next);
    }

    @RequiresPermission("mail:profile:manage") @Transactional
    public View disable(long id, long expectedVersion) {
        var row = owned(id, true); checkVersion(row, expectedVersion);
        // Emergency disable must work even if the encryption key service is unavailable.
        // Advance management version so stale edits cannot accidentally re-enable the account.
        row.setEnabled(false); row.setVersion(Math.addExact(row.getVersion(), 1));
        profiles.updateById(row); return view(row);
    }

    private MailProfileEntity owned(long id, boolean lock) {
        long tenant = ActorContext.tenantId();
        var query = new LambdaQueryWrapper<MailProfileEntity>().eq(MailProfileEntity::getId, id);
        if (lock) query.last("FOR UPDATE");
        var row = profiles.selectOne(query);
        if (row == null || row.getTenantId() != tenant) throw new BizException(MailErrorCode.PROFILE_NOT_FOUND);
        return row;
    }

    private void checkVersion(MailProfileEntity row, long expected) {
        if (expected <= 0 || row.getVersion() != expected) throw new BizException(MailErrorCode.VERSION_CONFLICT);
    }

    private MailProfileRevisionEntity revision(MailProfileEntity row) {
        var revision = revisions.selectOne(new LambdaQueryWrapper<MailProfileRevisionEntity>()
                .eq(MailProfileRevisionEntity::getProfileId, row.getId())
                .eq(MailProfileRevisionEntity::getConfigurationVersion, row.getConfigurationVersion()));
        if (revision == null || !revision.getTenantId().equals(row.getTenantId())) throw new BizException(MailErrorCode.PROFILE_NOT_FOUND);
        return revision;
    }

    private MailProfileRevisionEntity revision(MailProfileEntity row, Input input, char[] password) {
        var value = new MailProfileRevisionEntity();
        value.setProfileId(row.getId()); value.setConfigurationVersion(row.getConfigurationVersion());
        value.setCredentialVersion(row.getCredentialVersion()); value.setHost(input.host().toLowerCase(Locale.ROOT));
        value.setPort(input.port()); value.setTlsMode(input.tlsMode().name()); value.setUsername(input.username());
        value.setSecretEnvelope(secrets.encrypt(reference(row), "smtp-password", password));
        value.setFromAddress(input.fromAddress()); value.setFromName(input.fromName());
        value.setApplicationsJson(json.writeValueAsString(input.applications().stream().sorted().toList()));
        value.setDailyQuota(input.dailyQuota()); value.setPerMinuteLimit(input.perMinuteLimit());
        return value;
    }

    private TenantConfigurationRef reference(MailProfileEntity row) {
        return new TenantConfigurationRef(row.getTenantId(), "mail", row.getId(), row.getConfigurationVersion(), row.getCredentialVersion());
    }

    private View view(MailProfileEntity row) { return view(row, revision(row)); }
    private View view(MailProfileEntity row, MailProfileRevisionEntity revision) {
        List<String> applications = json.readValue(revision.getApplicationsJson(), new TypeReference<List<String>>() {});
        return new View(row.getId(), row.getCode(), row.getName(), row.getEnabled(), row.getVersion(), row.getConfigurationVersion(),
                row.getCredentialVersion(), revision.getHost(), revision.getPort(), TlsMode.valueOf(revision.getTlsMode()),
                revision.getUsername(), revision.getSecretEnvelope() != null, revision.getFromAddress(), revision.getFromName(),
                List.copyOf(applications), revision.getDailyQuota(), revision.getPerMinuteLimit());
    }

    private void validate(Input input) {
        if (input == null || !validator.validate(input).isEmpty()) throw invalid();
        String host = input.host();
        if (!host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?")
                || host.contains("..") || input.username().chars().anyMatch(Character::isISOControl)
                || input.fromName().chars().anyMatch(Character::isISOControl)
                || input.fromAddress().chars().anyMatch(Character::isISOControl)
                || input.applications().stream().anyMatch(java.util.Objects::isNull)) throw invalid();
        // No network operation here. Sender separately checks deployment egress allowlist, DNS/IP and TLS.
    }

    private static BizException invalid() { return new BizException(MailErrorCode.INVALID_CONFIGURATION); }
}
