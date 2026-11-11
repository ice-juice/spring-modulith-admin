package com.sma.admin.config;

import java.util.Collection;
import java.util.List;

/** Supported static-module registration contract. The host validates the actual provider module and namespace. */
public interface ConfigurationCatalog {
    default Collection<ParameterDefinition> parameters() { return List.of(); }
    default Collection<DictionaryDefinition> dictionaries() { return List.of(); }
}
