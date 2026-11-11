package com.sma.admin.config;

import java.util.Optional;

/** Additional supported query contract; existing ParameterQuery and DictionaryQuery signatures remain unchanged. */
public interface EffectiveConfigurationQuery {
    Optional<ParameterSnapshot> parameter(String key);
    Optional<DictionarySnapshot> dictionary(String key);
}
