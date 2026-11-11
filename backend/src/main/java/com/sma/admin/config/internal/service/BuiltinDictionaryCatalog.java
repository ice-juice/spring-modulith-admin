package com.sma.admin.config.internal.service;

import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.config.*;

/** Pure seed metadata. Existing built-ins remain tenant-extendable; codes and parents are stable. */
@Component
public class BuiltinDictionaryCatalog implements ConfigurationCatalog {
    @Override public Collection<DictionaryDefinition> dictionaries() {
        return BuiltinDictionaries.seeds().stream().map(seed->{
            var items=new ArrayList<DictionaryDefinition.Item>();
            for(int i=0;i<seed.items().size();i++) {
                var item=seed.items().get(i);
                String parent=item.parentIndex()==null?null:seed.items().get(item.parentIndex()).value();
                items.add(new DictionaryDefinition.Item(item.value(),parent,new ConfigurationLabel(item.name(),item.nameKey(),Map.of()),true,i));
            }
            return new DictionaryDefinition("config",seed.key(),new ConfigurationLabel(seed.name(),seed.nameKey(),Map.of()),
                    DictionaryDefinition.Mode.EXTENDABLE,seed.tree(),items);
        }).toList();
    }
}
