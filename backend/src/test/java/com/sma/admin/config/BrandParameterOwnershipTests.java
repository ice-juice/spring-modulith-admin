package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;

class BrandParameterOwnershipTests {
    @Test void mysqlCaseInsensitiveAliasesCannotRecreateOrReadASecondBrandSource() {
        var mapper=mock(ParameterMapper.class);var groups=mock(ParameterGroupMapper.class);var names=mock(ConfigNames.class);
        var lock=mock(WriteLockMapper.class);var events=mock(org.springframework.context.ApplicationEventPublisher.class);var cache=mock(ManagedCache.class);
        var service=new ParameterService(mapper,groups,names,lock,events,tools.jackson.databind.json.JsonMapper.builder().build(),cache,
                mock(ConfigurationCatalogRegistry.class),mock(DefinedParameterReader.class),mock(DefinedParameterService.class));
        for(var key:LegacyBrandSource.Key.values()) {
            for(String variant:List.of(key.parameter(),key.parameter().toUpperCase(Locale.ROOT),key.parameter().toLowerCase(Locale.ROOT))) {
                var input=new ParameterService.Input(variant,"basic","Brand",null,"STRING","value",true,false,0,Map.of());
                assertThatThrownBy(()->service.save(null,input)).isInstanceOf(BizException.class)
                        .satisfies(failure->assertThat(((BizException)failure).getErrorCode()).isEqualTo(ConfigErrorCode.BRAND_MANAGED));
                assertThatThrownBy(()->service.stringValue(variant,"")).isInstanceOf(BizException.class);
            }
        }
        verifyNoInteractions(mapper,groups,names,lock,events,cache);
    }
}
