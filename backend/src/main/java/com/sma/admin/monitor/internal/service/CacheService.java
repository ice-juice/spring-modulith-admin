package com.sma.admin.monitor.internal.service;
import java.util.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CacheRegion;
@Service
public class CacheService {
 private final Map<String,CacheRegion> caches;private final MessageSource messages;
 public CacheService(List<CacheRegion> caches,MessageSource messages){this.messages=messages;Map<String,CacheRegion> regions=new LinkedHashMap<>();for(var cache:caches)if(regions.put(cache.id(),cache)!=null)throw new IllegalStateException("Duplicate managed cache: "+cache.id());this.caches=Map.copyOf(regions);}
 public record View(String id,String name,long size,long hits,long misses){}
 public List<View> list(){return caches.values().stream().map(CacheRegion::snapshot).sorted(Comparator.comparing(CacheRegion.Snapshot::id))
  .map(row->new View(row.id(),messages.getMessage(row.nameKey(),null,row.id(),LocaleContextHolder.getLocale()),row.size(),row.hits(),row.misses())).toList();}
 public void clear(String id){var cache=caches.get(id);if(cache==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);cache.clear();}
 public void clearAll(){caches.values().forEach(CacheRegion::clear);}
}
