package com.sma.admin.config.internal.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;

/** Namespace claims join the configuration write. They are never independently committed or publicly enumerated. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class DictionaryCodeRegistry {
    public static final long MAX_CODES=65536;
    private final DictionaryCodeMapper codes;
    private final SharedHeadMapper heads;
    private final ConfigurationCatalogRegistry catalog;
    public DictionaryCodeRegistry(DictionaryCodeMapper codes,SharedHeadMapper heads,ConfigurationCatalogRegistry catalog){this.codes=codes;this.heads=heads;this.catalog=catalog;}

    /** Caller owns this head's exclusive lock and persists the updated count together with its publication. */
    public void reserveShared(SharedHeadEntity head,List<DictionaryDefinition.Item> items) {
        if(!"DICTIONARY".equals(head.getKind())) throw new BizException(ConfigErrorCode.SHARED_INVALID);
        for(var item:items) reserve(head,item.code(),"SHARED");
    }
    /** Caller locks its tenant guard first and must invoke this BEFORE a shared-pointer read. */
    public void reservePrivate(String dictionary,String code) {
        CurrentUser.tenantId();
        var entry=catalog.find(Kind.DICTIONARY,dictionary).orElseThrow(()->new BizException(ConfigErrorCode.SHARED_INVALID));
        if(entry.dictionary().mode()!=DictionaryDefinition.Mode.EXTENDABLE)throw new BizException(ConfigErrorCode.SHARED_INVALID);
        var head=heads.lockKey(Kind.DICTIONARY.name(),entry.key());
        if(head==null || !entry.contractHash().equals(head.getContractHash()) || !entry.owner().equals(head.getOwnerModule()) || head.getPublishedRevisionId()==null)
            throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        long before=head.getDictionaryCodeCount();reserve(head,code,"PRIVATE");
        if(before!=head.getDictionaryCodeCount())heads.updateById(head);
    }
    private void reserve(SharedHeadEntity head,String code,String ownership) {
        final String digest;
        try{digest=DictionaryCodeIdentity.digest(code);}catch(IllegalArgumentException invalid){throw new BizException(ConfigErrorCode.SHARED_INVALID);}
        var existing=codes.current(head.getId(),digest);
        if(existing!=null) {
            if(Boolean.TRUE.equals(existing.getDeleted()))throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
            if(!ownership.equals(existing.getOwnership()))throw new BizException(ConfigErrorCode.DICTIONARY_CODE_CONFLICT);
            return;
        }
        Long count=head.getDictionaryCodeCount();
        if(count==null || count<0)throw new BizException(ConfigErrorCode.SHARED_UNAVAILABLE);
        if(count>=MAX_CODES)throw new BizException(ConfigErrorCode.DICTIONARY_CODE_CAPACITY);
        var row=new DictionaryCodeEntity();row.setHeadId(head.getId());row.setCodeHash(digest);row.setOwnership(ownership);codes.insert(row);
        head.setDictionaryCodeCount(count+1);
    }
}
