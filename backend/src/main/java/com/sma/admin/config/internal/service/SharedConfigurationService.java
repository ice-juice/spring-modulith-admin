package com.sma.admin.config.internal.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.security.TenantEntryScope;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Entry;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;

/** Platform-resource transactions only; no network and no customer override storage. */
@Service @Transactional(readOnly=true)
public class SharedConfigurationService {
    public static final int MAX_CONTENT_BYTES = 256 * 1024;
    public static final long MAX_HISTORY_BYTES = 32L * 1024 * 1024;
    public static final long MAX_REVISIONS = 1000;
    private final SharedHeadMapper heads;
    private final SharedRevisionMapper revisions;
    private final ConfigurationCatalogRegistry catalog;
    private final ConfigurationPlatformGuard access;
    private final JsonMapper json;
    private final SharedConfigurationRules rules;
    private final DictionaryCodeRegistry dictionaryCodes;
    public SharedConfigurationService(SharedHeadMapper heads, SharedRevisionMapper revisions, ConfigurationCatalogRegistry catalog,
            ConfigurationPlatformGuard access, JsonMapper json, DictionaryCodeRegistry dictionaryCodes) {
        this.heads = heads; this.revisions = revisions; this.catalog = catalog; this.access = access; this.json = json; this.rules = new SharedConfigurationRules(json);
        this.dictionaryCodes=dictionaryCodes;
    }
    public record ParameterContent(int schemaVersion, ConfigurationLabel label, String value) {
        @Override public String toString() { return "SharedParameterContent[schemaVersion=" + schemaVersion + "]"; }
    }
    public record DictionaryContent(int schemaVersion, ConfigurationLabel label, List<DictionaryDefinition.Item> items) {
        public DictionaryContent { items = items == null ? List.of() : List.copyOf(items); }
    }
    public record Summary(Long id, Kind kind, String key, String owner, ConfigurationLabel label, long draftVersion,
            Long publishedRevisionId, long publishedRevision, boolean codeDefaultsChanged) {}
    public record Detail(Long id, Kind kind, String key, String owner, ParameterDefinition parameterDefinition, DictionaryDefinition dictionaryDefinition,
            ParameterContent parameterDraft, DictionaryContent dictionaryDraft, long draftVersion, Long publishedRevisionId,
            long publishedRevision, boolean codeDefaultsChanged) {}
    public record Publication(Long revisionId, long revision) {}
    public record RevisionSummary(Long id, long revision, int contentBytes, Instant createdAt, Long createdBy) {}
    public record RevisionDetail(Long id, Long headId, long revision, ParameterContent parameter, DictionaryContent dictionary, Instant createdAt) {}
    /** Internal Config reader contract. Catalog ownership and the current shared pointer remain authoritative. */
    public record Published(Entry definition, long revision, Long revisionId, ParameterContent parameter, DictionaryContent dictionary) {}
    public record PublishedPointer(Entry definition,long headId,long revisionId,long revision) {}

    @Transactional public void initialize(Kind kind, String key) {
        if (SecurityContextHolder.getContext().getAuthentication() != null || RequestContextHolder.getRequestAttributes() != null || TenantEntryScope.isHttpRequest())
            throw new AccessDeniedException("Configuration registration is host startup work");
        var entry = catalog.require(kind, key); if (!entry.shared()) return;
        Object initial = initial(entry); String content = validated(entry, initial, null);
        var proposed = new SharedHeadEntity(); proposed.setId(IdWorker.getId()); proposed.setKind(kind.name()); proposed.setConfigKey(entry.key());
        proposed.setOwnerModule(entry.owner()); proposed.setDefinitionJson(entry.definitionJson()); proposed.setContractHash(entry.contractHash()); proposed.setSourceFingerprint(entry.fingerprint());
        proposed.setDraftJson(content); proposed.setDraftLabelJson(catalog.encode(contentLabel(initial)));
        heads.ensure(proposed, Instant.now());
        var row = heads.lockKey(kind.name(), entry.key());
        if (row == null || !row.getOwnerModule().equals(entry.owner()) || !row.getContractHash().equals(entry.contractHash()))
            throw new IllegalStateException("Shared configuration contract changed for " + entry.key() + "; an explicit migration is required");
        if (row.getPublishedRevisionId() == null) {
            if (!row.getId().equals(proposed.getId())) throw new IllegalStateException("Shared configuration publication is missing for " + entry.key());
            append(row, content);
        } else if(kind==Kind.DICTIONARY) {
            long before=row.getDictionaryCodeCount();
            dictionaryCodes.reserveShared(row,((DictionaryContent)decode(kind,current(row).getContentJson())).items());
            if(before!=row.getDictionaryCodeCount())heads.updateById(row);
        }
    }

    public PageResult<Summary> page(PageQuery page, Kind kind, String keyword) {
        access.require("config:shared:view"); pageSize(page);
        var selected = catalog.entries().stream().filter(Entry::shared).filter(item -> kind == null || item.kind() == kind).toList();
        var pagination = page.<SharedHeadEntity>toPage(Map.of("key", "config_key", "owner", "owner_module", "id", "id"));
        if (selected.isEmpty()) return new PageResult<>(List.of(), 0, pagination.getCurrent(), pagination.getSize());
        var query = new LambdaQueryWrapper<SharedHeadEntity>().select(SharedHeadEntity::getId, SharedHeadEntity::getKind, SharedHeadEntity::getConfigKey,
                SharedHeadEntity::getOwnerModule, SharedHeadEntity::getContractHash, SharedHeadEntity::getSourceFingerprint, SharedHeadEntity::getDraftLabelJson,
                SharedHeadEntity::getDraftVersion, SharedHeadEntity::getPublishedRevisionId, SharedHeadEntity::getNextRevision);
        query.and(group -> selected.forEach(item -> group.or(clause -> clause.eq(SharedHeadEntity::getKind, item.kind().name()).eq(SharedHeadEntity::getConfigKey, item.key()))));
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 128) throw new BizException(ConfigErrorCode.SHARED_INVALID);
            query.and(group -> group.like(SharedHeadEntity::getConfigKey, keyword).or().like(SharedHeadEntity::getOwnerModule, keyword));
        }
        query.orderByAsc(SharedHeadEntity::getKind).orderByAsc(SharedHeadEntity::getConfigKey);
        var result = heads.selectPage(pagination, query);
        return new PageResult<>(result.getRecords().stream().map(this::summary).toList(), result.getTotal(), result.getCurrent(), result.getSize());
    }
    public Detail get(long id) { access.require("config:shared:view"); return detail(require(id)); }

    @Transactional public Detail saveParameter(long id, long expectedVersion, ParameterContent content) { return save(id, expectedVersion, content, Kind.PARAMETER); }
    @Transactional public Detail saveDictionary(long id, long expectedVersion, DictionaryContent content) { return save(id, expectedVersion, content, Kind.DICTIONARY); }
    private Detail save(long id, long expectedVersion, Object content, Kind kind) {
        access.require("config:shared:edit"); var row = locked(id); var entry = entry(row);
        if (entry.kind() != kind) throw new BizException(ConfigErrorCode.SHARED_INVALID);
        version(row, expectedVersion);
        String encoded = validated(entry, content, current(row));
        row.setDraftJson(encoded); row.setDraftLabelJson(catalog.encode(contentLabel(content))); row.setDraftVersion(Math.addExact(row.getDraftVersion(), 1)); heads.updateById(row);
        return detail(row);
    }

    @Transactional public Publication publish(long id, long expectedDraftVersion, Long expectedPublishedRevisionId) {
        access.require("config:shared:publish"); var row = locked(id); var entry = entry(row); version(row, expectedDraftVersion);
        if (!Objects.equals(expectedPublishedRevisionId, row.getPublishedRevisionId())) throw new BizException(ConfigErrorCode.SHARED_STALE);
        var previous = current(row); Object content = decode(entry.kind(), row.getDraftJson());
        return append(row, validated(entry, content, previous));
    }

    public PageResult<RevisionSummary> history(long id, PageQuery page) {
        access.require("config:shared:view"); require(id); pageSize(page);
        var result = revisions.selectPage(page.<SharedRevisionEntity>toPage(Map.of()), new LambdaQueryWrapper<SharedRevisionEntity>()
                .select(SharedRevisionEntity::getId, SharedRevisionEntity::getRevision, SharedRevisionEntity::getContentBytes, SharedRevisionEntity::getCreatedAt, SharedRevisionEntity::getCreatedBy)
                .eq(SharedRevisionEntity::getHeadId, id).orderByDesc(SharedRevisionEntity::getRevision));
        return new PageResult<>(result.getRecords().stream().map(row -> new RevisionSummary(row.getId(), row.getRevision(), row.getContentBytes(), row.getCreatedAt(), row.getCreatedBy())).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }
    public RevisionDetail revision(long id, long revisionId) {
        access.require("config:shared:view"); var head = require(id); var row = revisions.current(revisionId, id);
        if (row == null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        var content = decode(entry(head).kind(), row.getContentJson());
        return new RevisionDetail(row.getId(), id, row.getRevision(), content instanceof ParameterContent value ? value : null,
                content instanceof DictionaryContent value ? value : null, row.getCreatedAt());
    }

    /** Trusted tenant reads do not acquire platform management authority and never see a draft. */
    public Optional<Published> published(Kind kind, String key) {
        CurrentUser.tenantId(); var definition = catalog.find(kind, key);
        if (definition.isEmpty() || !definition.get().shared()) return Optional.empty();
        var row = heads.current(kind.name(), definition.get().key());
        if (row == null) throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        var entry = entry(row); var revision = current(row); var content = decode(kind, revision.getContentJson());
        return Optional.of(new Published(entry, revision.getRevision(), revision.getId(), content instanceof ParameterContent value ? value : null,
                content instanceof DictionaryContent value ? value : null));
    }

    /** Capture the authoritative pointer; immutable content may be loaded after this short transaction ends. */
    public Optional<PublishedPointer> pointer(Kind kind,String key) {
        CurrentUser.tenantId(); var definition=catalog.find(kind,key);
        if(definition.isEmpty() || !definition.get().shared()) return Optional.empty();
        var row=heads.pointer(kind.name(),definition.get().key());
        if(row==null || row.getPublishedRevisionId()==null || row.getNextRevision()==null || row.getNextRevision()<2)
            throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        return Optional.of(new PublishedPointer(entry(row),row.getId(),row.getPublishedRevisionId(),row.getNextRevision()-1));
    }
    public Published publication(PublishedPointer pointer) {
        CurrentUser.tenantId();
        var registered=catalog.find(pointer.definition().kind(),pointer.definition().key());
        if(registered.isEmpty() || !registered.get().equals(pointer.definition())) throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        var row=revisions.current(pointer.revisionId(),pointer.headId());
        if(row==null || row.getRevision()!=pointer.revision() || !row.getContractHash().equals(pointer.definition().contractHash()))
            throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        var content=decode(pointer.definition().kind(),row.getContentJson());
        return new Published(pointer.definition(),row.getRevision(),row.getId(),content instanceof ParameterContent value?value:null,
                content instanceof DictionaryContent value?value:null);
    }

    private Publication append(SharedHeadEntity row, String content) {
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (row.getNextRevision() > MAX_REVISIONS || bytes > MAX_CONTENT_BYTES || row.getHistoryBytes() > MAX_HISTORY_BYTES - bytes) throw new BizException(ConfigErrorCode.SHARED_CAPACITY);
        if("DICTIONARY".equals(row.getKind()))dictionaryCodes.reserveShared(row,((DictionaryContent)decode(Kind.DICTIONARY,content)).items());
        var revision = new SharedRevisionEntity(); revision.setHeadId(row.getId()); revision.setRevision(row.getNextRevision()); revision.setContractHash(row.getContractHash());
        revision.setContentJson(content); revision.setContentBytes(bytes); revisions.insert(revision);
        row.setPublishedRevisionId(revision.getId()); row.setNextRevision(Math.addExact(row.getNextRevision(), 1)); row.setHistoryBytes(Math.addExact(row.getHistoryBytes(), bytes)); heads.updateById(row);
        return new Publication(revision.getId(), revision.getRevision());
    }
    private String validated(Entry entry, Object content, SharedRevisionEntity previous) {
        try {
            if (entry.kind() == Kind.PARAMETER) {
                if (!(content instanceof ParameterContent parameter) || parameter.schemaVersion() != 1) throw new IllegalArgumentException();
                rules.validateLabel(parameter.label());
                rules.parameter(entry.parameter(), Optional.of(new SharedConfigurationRules.Value(parameter.value())), Optional.empty(), new ConfigurationRevision(0, 0));
            } else {
                if (!(content instanceof DictionaryContent dictionary) || dictionary.schemaVersion() != 1) throw new IllegalArgumentException();
                rules.validateLabel(dictionary.label());
                var before = previous == null ? List.<DictionaryDefinition.Item>of() : ((DictionaryContent) decode(Kind.DICTIONARY, previous.getContentJson())).items();
                rules.publication(entry.dictionary(), before, dictionary.items());
            }
            String result = catalog.encode(content);
            if (result.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) throw new IllegalArgumentException();
            return result;
        } catch (IllegalArgumentException | NullPointerException failure) { throw new BizException(ConfigErrorCode.SHARED_INVALID); }
    }
    private Object initial(Entry entry) {
        return entry.kind() == Kind.PARAMETER ? new ParameterContent(1, entry.parameter().label(), entry.parameter().defaultValue())
                : new DictionaryContent(1, entry.dictionary().label(), entry.dictionary().items());
    }
    private ConfigurationLabel contentLabel(Object content) { return content instanceof ParameterContent parameter ? parameter.label() : ((DictionaryContent) content).label(); }
    private Object decode(Kind kind, String content) {
        try {
            Object value = kind == Kind.PARAMETER ? json.readValue(content, ParameterContent.class) : json.readValue(content, DictionaryContent.class);
            int version = value instanceof ParameterContent parameter ? parameter.schemaVersion() : value instanceof DictionaryContent dictionary ? dictionary.schemaVersion() : -1;
            if (version != 1) throw new IllegalArgumentException();
            rules.validateLabel(contentLabel(value));
            return value;
        } catch (RuntimeException invalid) {
            // Unsupported/corrupt persisted data is not a fallback and must not leak raw JSON through parser diagnostics.
            throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        }
    }
    private Entry entry(SharedHeadEntity row) {
        var entry = catalog.find(Kind.valueOf(row.getKind()), row.getConfigKey()).orElseThrow(() -> new BizException(ConfigErrorCode.SHARED_UNAVAILABLE));
        if (!entry.shared() || !entry.owner().equals(row.getOwnerModule()) || !entry.contractHash().equals(row.getContractHash())) throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        return entry;
    }
    private SharedHeadEntity require(long id) { var row = heads.selectById(id); if (row == null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND); entry(row); return row; }
    private SharedHeadEntity locked(long id) { var row = heads.lock(id); if (row == null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND); entry(row); return row; }
    private SharedRevisionEntity current(SharedHeadEntity row) {
        if (row.getPublishedRevisionId() == null) throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        var revision = revisions.current(row.getPublishedRevisionId(), row.getId());
        if (revision == null || !revision.getContractHash().equals(row.getContractHash())) throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        return revision;
    }
    private Summary summary(SharedHeadEntity row) { var entry = entry(row); return new Summary(row.getId(), entry.kind(), entry.key(), entry.owner(), json.readValue(row.getDraftLabelJson(), ConfigurationLabel.class),
            row.getDraftVersion(), row.getPublishedRevisionId(), row.getNextRevision() - 1, !row.getSourceFingerprint().equals(entry.fingerprint())); }
    private Detail detail(SharedHeadEntity row) { var entry = entry(row); var content = decode(entry.kind(), row.getDraftJson());
        return new Detail(row.getId(), entry.kind(), entry.key(), entry.owner(), entry.parameter(), entry.dictionary(), content instanceof ParameterContent value ? value : null,
                content instanceof DictionaryContent value ? value : null, row.getDraftVersion(), row.getPublishedRevisionId(), row.getNextRevision() - 1, !row.getSourceFingerprint().equals(entry.fingerprint())); }
    private void version(SharedHeadEntity row, long version) { if (version < 1 || version != row.getDraftVersion()) throw new BizException(ConfigErrorCode.SHARED_STALE); }
    private void pageSize(PageQuery page) { if (page.size() != null && page.size() > 50) throw new BizException(CommonErrorCode.BAD_REQUEST); }
}
