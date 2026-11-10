package com.sma.admin.config.internal.service;
import java.util.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.config.internal.persistence.*;

@Service
public class ConfigNames {
    private final I18nTextMapper mapper;
    private final MessageSource messages;
    public ConfigNames(I18nTextMapper mapper,MessageSource messages) { this.mapper=mapper; this.messages=messages; }
    public Map<String,String> translations(String type,long id) {
        Map<String,String> result=new HashMap<>();
        mapper.selectList(query(type,id)).forEach(row -> result.put(row.getLocale(),row.getText()));
        return result;
    }
    public String name(String fallback,String key,Map<String,String> translations) {
        var locale=LocaleContextHolder.getLocale();
        if(translations.containsKey(locale.toLanguageTag())) return translations.get(locale.toLanguageTag());
        return key==null||key.isBlank()?fallback:messages.getMessage(key,null,fallback,locale);
    }
    public void replace(String type,long id,Map<String,String> values) {
        Map<String,String> texts=values==null?Map.of():Map.copyOf(values);
        if(!Set.of("zh-CN","en-US").containsAll(texts.keySet())||texts.values().stream().anyMatch(text -> text.isBlank()||text.length()>128)) throw new BizException(CommonErrorCode.BAD_REQUEST);
        mapper.delete(query(type,id));
        texts.forEach((locale,text) -> { var row=new I18nTextEntity(); row.setEntityType(type); row.setEntityId(id); row.setLocale(locale); row.setText(text); mapper.insert(row); });
    }
    private LambdaQueryWrapper<I18nTextEntity> query(String type,long id) { return new LambdaQueryWrapper<I18nTextEntity>().eq(I18nTextEntity::getEntityType,type).eq(I18nTextEntity::getEntityId,id); }
}
