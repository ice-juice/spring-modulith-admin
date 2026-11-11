package com.sma.admin.mail.internal.service;

import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.persistence.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service @Transactional(readOnly = true)
public class MailTemplateService {
    public record Input(String code, String application, String scene, String language, String profileCode, MailTemplateContent content) {
        @Override public String toString() { return "MailTemplateInput[REDACTED]"; }
    }
    public record View(Long id, String code, String application, String scene, String language, String profileCode,
                       Long version, Long publishedVersion, boolean enabled, MailTemplateContent content) {}
    private final MailTemplateMapper templates;
    private final MailTemplateVersionMapper versions;
    private final MailProfileMapper profiles;
    private final MailProfileRevisionMapper profileVersions;
    private final MailTemplateRenderer renderer;
    private final JsonMapper json;
    public MailTemplateService(MailTemplateMapper templates, MailTemplateVersionMapper versions, MailProfileMapper profiles,
                               MailProfileRevisionMapper profileVersions, MailTemplateRenderer renderer, JsonMapper json) {
        this.templates = templates; this.versions = versions; this.profiles = profiles;
        this.profileVersions = profileVersions; this.renderer = renderer; this.json = json;
    }

    @RequiresPermission("mail:template:view")
    public PageResult<View> page(PageQuery page, String keyword) {
        ActorContext.tenantId(); var query = new LambdaQueryWrapper<MailTemplateEntity>().orderByDesc(MailTemplateEntity::getCreatedAt).orderByDesc(MailTemplateEntity::getId);
        if (keyword != null && !keyword.isBlank()) query.like(MailTemplateEntity::getCode, keyword);
        return PageResult.of(templates.selectPage(page.toPage(Map.of("code", "code", "createdAt", "created_at")), query), this::view);
    }
    @RequiresPermission("mail:template:view") public View get(long id) { return view(owned(id, false)); }

    @RequiresPermission("mail:template:manage") @Transactional
    public View create(Input input) {
        validate(input); ActorContext.tenantId(); var row = new MailTemplateEntity();
        row.setCode(input.code()); row.setApplication(input.application()); row.setScene(input.scene()); row.setLanguage(input.language());
        row.setProfileCode(input.profileCode()); row.setContentJson(json.writeValueAsString(input.content()));
        row.setVersion(1L); row.setPublishedVersion(0L); row.setEnabled(false);
        try { templates.insert(row); } catch (DuplicateKeyException duplicate) { throw new BizException(MailErrorCode.DUPLICATE_CODE); }
        return view(row);
    }

    @RequiresPermission("mail:template:manage") @Transactional
    public View update(long id, long expectedVersion, Input input) {
        validate(input); var row = owned(id, true); checkVersion(row, expectedVersion);
        if (!row.getCode().equals(input.code()) || !row.getApplication().equals(input.application())
                || !row.getScene().equals(input.scene()) || !row.getLanguage().equals(input.language())) throw invalid();
        row.setProfileCode(input.profileCode()); row.setContentJson(json.writeValueAsString(input.content()));
        row.setVersion(Math.addExact(row.getVersion(), 1)); templates.updateById(row); return view(row);
    }

    @RequiresPermission("mail:template:publish") @Transactional
    public View publish(long id, long expectedVersion) {
        var row = owned(id, true); checkVersion(row, expectedVersion);
        renderer.validate(json.readValue(row.getContentJson(), MailTemplateContent.class));
        var profile = profiles.selectOne(new LambdaQueryWrapper<MailProfileEntity>().eq(MailProfileEntity::getCode, row.getProfileCode()).last("FOR UPDATE"));
        if (profile == null || !profile.getEnabled()) throw new BizException(MailErrorCode.PROFILE_NOT_FOUND);
        var profileVersion = profileVersions.selectOne(new LambdaQueryWrapper<MailProfileRevisionEntity>()
                .eq(MailProfileRevisionEntity::getProfileId, profile.getId()).eq(MailProfileRevisionEntity::getConfigurationVersion, profile.getConfigurationVersion()));
        if (profileVersion == null || !json.readValue(profileVersion.getApplicationsJson(), new TypeReference<List<String>>() {}).contains(row.getApplication())) throw invalid();
        // Publishing is an explicit new version, including when only the selected account changed.
        var published = new MailTemplateVersionEntity(); published.setTemplateId(id);
        published.setVersion(Math.addExact(row.getVersion(), 1)); published.setContentJson(row.getContentJson());
        published.setProfileId(profile.getId()); published.setProfileVersion(profile.getConfigurationVersion());
        published.setCredentialVersion(profile.getCredentialVersion()); versions.insert(published);
        row.setVersion(published.getVersion()); row.setPublishedVersion(published.getVersion()); row.setEnabled(true);
        templates.updateById(row); return view(row);
    }

    @RequiresPermission("mail:template:publish") @Transactional
    public View disable(long id, long expectedVersion) {
        var row = owned(id, true); checkVersion(row, expectedVersion); row.setEnabled(false);
        row.setVersion(Math.addExact(row.getVersion(), 1)); templates.updateById(row); return view(row);
    }

    @RequiresPermission("mail:template:view")
    public MailTemplateRenderer.Rendered preview(long id, Map<String, String> values) {
        return renderer.render(json.readValue(owned(id, false).getContentJson(), MailTemplateContent.class), values);
    }

    private MailTemplateEntity owned(long id, boolean lock) {
        ActorContext.tenantId(); var query = new LambdaQueryWrapper<MailTemplateEntity>().eq(MailTemplateEntity::getId, id);
        if (lock) query.last("FOR UPDATE"); var row = templates.selectOne(query);
        if (row == null) throw new BizException(MailErrorCode.TEMPLATE_NOT_FOUND); return row;
    }
    private void checkVersion(MailTemplateEntity row, long version) {
        if (version <= 0 || row.getVersion() != version) throw new BizException(MailErrorCode.VERSION_CONFLICT);
    }
    private View view(MailTemplateEntity row) {
        return new View(row.getId(), row.getCode(), row.getApplication(), row.getScene(), row.getLanguage(), row.getProfileCode(),
                row.getVersion(), row.getPublishedVersion(), row.getEnabled(), json.readValue(row.getContentJson(), MailTemplateContent.class));
    }
    private void validate(Input input) {
        if (input == null || !code(input.code()) || !code(input.application()) || !code(input.scene()) || !code(input.profileCode())
                || input.language() == null || !List.of("zh-CN", "en-US").contains(input.language())) throw invalid();
        renderer.validate(input.content());
    }
    private boolean code(String value) { return value != null && value.matches("[a-z][a-z0-9.-]{0,63}"); }
    private BizException invalid() { return new BizException(MailErrorCode.INVALID_TEMPLATE); }
}
