package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;

class SharedConfigurationReadTests {
    private record Catalog(List<ParameterDefinition> parameters) implements ConfigurationCatalog {}
    private final JsonMapper json = JsonMapper.builder().build();
    private final ParameterDefinition definition = new ParameterDefinition("config", "config.read", "basic", new ConfigurationLabel("Read", null, Map.of()),
            ParameterDefinition.Scope.PLATFORM_SHARED, ParameterDefinition.ValueType.STRING, ParameterDefinition.OverridePolicy.OVERRIDABLE, "default",
            ParameterDefinition.Constraints.required(), false, ParameterDefinition.PublicProjection.NONE);
    private final ConfigurationCatalogRegistry catalog = new ConfigurationCatalogRegistry(List.of(new Catalog(List.of(definition))), json);
    private final SharedHeadMapper heads = mock(SharedHeadMapper.class);
    private final SharedRevisionMapper revisions = mock(SharedRevisionMapper.class);
    private final SharedConfigurationService shared = new SharedConfigurationService(heads, revisions, catalog, mock(ConfigurationPlatformGuard.class), json,mock(DictionaryCodeRegistry.class));
    private SharedHeadEntity prepare(String content) {
        var row = new SharedHeadEntity(); row.setId(10L); row.setKind("PARAMETER"); row.setConfigKey("config.read"); row.setOwnerModule("config");
        row.setContractHash(catalog.require(Kind.PARAMETER, "config.read").contractHash()); row.setPublishedRevisionId(11L); row.setDraftJson("private-draft-must-not-be-read");
        var revision = new SharedRevisionEntity(); revision.setId(11L); revision.setHeadId(10L); revision.setRevision(1L); revision.setContractHash(row.getContractHash()); revision.setContentJson(content);
        when(heads.current("PARAMETER", "config.read")).thenReturn(row); when(revisions.current(11L, 10L)).thenReturn(revision); return row;
    }
    private Optional<SharedConfigurationService.Published> read() { return TenantScope.within(88, () -> shared.published(Kind.PARAMETER, "config.read")); }
    @Test void onlyTheCurrentPublicationIsReadAndUnknownKeysAreDistinctFromUnavailableDefinitions() {
        prepare("{\"schemaVersion\":1,\"label\":{\"text\":\"Published\"},\"value\":\"live\"}");
        assertThat(read().orElseThrow().parameter().value()).isEqualTo("live");
        verify(heads, never()).selectById(anyLong()); verify(revisions).current(11L, 10L);
        assertThat(TenantScope.within(88, () -> shared.published(Kind.PARAMETER, "unknown.key"))).isEmpty();
        when(heads.current("PARAMETER", "config.read")).thenReturn(null);
        assertThatThrownBy(this::read).isInstanceOfSatisfying(BizException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(ConfigErrorCode.SHARED_UNAVAILABLE));
    }
    @Test void unknownSchemaNullAndInvalidPersistedDocumentsAreRejectedWithoutRawParserContent() {
        for (String body : List.of("{\"schemaVersion\":2,\"label\":{\"text\":\"Future\"},\"value\":\"unreadable-value\"}", "null", "unreadable-value", "{\"schemaVersion\":1,\"value\":\"unreadable-value\"}")) {
            prepare(body);
            assertThatThrownBy(this::read).isInstanceOfSatisfying(BizException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(ConfigErrorCode.SHARED_UNAVAILABLE))
                    .hasNoCause().hasMessageNotContaining("unreadable-value");
        }
    }
    @Test void incompatibleContractsDoNotReadAnOldPublicationOrUseCodeDefaults() {
        var row = prepare("{\"schemaVersion\":1,\"label\":{\"text\":\"Published\"},\"value\":\"live\"}"); row.setContractHash("0".repeat(64));
        assertThatThrownBy(this::read).isInstanceOfSatisfying(BizException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(ConfigErrorCode.SHARED_UNAVAILABLE));
        verifyNoInteractions(revisions);
    }
}
