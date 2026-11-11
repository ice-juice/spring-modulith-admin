package com.sma.admin.config.internal.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import com.sma.admin.config.*;

/** Supported effective-value contract for static modules. Unregistered private configuration keeps its legacy APIs. */
@Service
public class EffectiveConfigurationService implements EffectiveConfigurationQuery {
    private final DefinedParameterReader parameters;
    private final DefinedDictionaryReader dictionaries;
    public EffectiveConfigurationService(DefinedParameterReader parameters,DefinedDictionaryReader dictionaries){this.parameters=parameters;this.dictionaries=dictionaries;}
    @Override public Optional<ParameterSnapshot> parameter(String key){return parameters.read(key);}
    @Override public Optional<DictionarySnapshot> dictionary(String key){return dictionaries.read(key);}
}
