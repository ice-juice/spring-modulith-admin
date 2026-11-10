package com.sma.admin.config;
import java.util.List;
public record DictionaryOption(String value,String label,List<DictionaryOption> children) {
    public DictionaryOption { children=List.copyOf(children); }
}
