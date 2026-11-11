package com.sma.admin.monitor.internal.service;
import java.util.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CacheRegion;
import com.sma.admin.common.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.*;
@Service
public class CacheService {
 private final Map<String,CacheRegion> caches;private final MessageSource messages;
 private final Map<String,CacheDescriptor> descriptors;
 public CacheService(List<CacheRegion> caches,MessageSource messages,CacheRegions catalog,ManagedCache managed){
  this.messages=messages;Map<String,CacheRegion> regions=new LinkedHashMap<>();
  for(var cache:caches)if(regions.put(cache.id(),cache)!=null)throw new IllegalStateException("Duplicate managed cache: "+cache.id());
  for(var descriptor:catalog.regions())if(descriptor.kind()==CacheDescriptor.Kind.DATA&&!regions.containsKey(descriptor.id()))
   regions.put(descriptor.id(),new ManagedRegion(descriptor,managed));
  this.caches=Map.copyOf(regions);
  this.descriptors=catalog.regions().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(CacheDescriptor::id,descriptor->descriptor));
 }
 private record ManagedRegion(CacheDescriptor descriptor,ManagedCache cache) implements CacheRegion {
  @Override public String id(){return descriptor.id();}
  @Override public Snapshot snapshot(){var row=cache.snapshot(id());return new Snapshot(id(),descriptor.nameKey(),row.size(),row.hits(),row.misses());}
  @Override public void clear(){if(!descriptor.operations().contains(CacheDescriptor.Operation.CLEAR))throw new BizException(CommonErrorCode.FORBIDDEN);cache.invalidate(id());}
 }
 public record View(String id,String name,long size,long hits,long misses){}
 public List<View> list(){return caches.values().stream().map(CacheRegion::snapshot).sorted(Comparator.comparing(CacheRegion.Snapshot::id))
  .map(row->new View(row.id(),messages.getMessage(row.nameKey(),null,row.id(),LocaleContextHolder.getLocale()),row.size(),row.hits(),row.misses())).toList();}
 public void clear(String id){
  requireOperation();var cache=caches.get(id);if(cache==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
  var descriptor=descriptors.get(id);
  if(descriptor==null||!allowed(descriptor))throw new AccessDeniedException("Cache region operation denied");
  cache.clear();
 }
 public void clearAll(){
  requireOperation();
  caches.values().stream().sorted(Comparator.comparing(CacheRegion::id))
   .filter(cache->descriptors.containsKey(cache.id())&&allowed(descriptors.get(cache.id()))).forEach(CacheRegion::clear);
 }
 private void requireOperation(){if(!CurrentUser.require().permissions().contains("monitor:cache:clear"))throw new AccessDeniedException("Cache operation denied");}
 private boolean allowed(CacheDescriptor descriptor){return descriptor.kind()==CacheDescriptor.Kind.DATA&&descriptor.operations().contains(CacheDescriptor.Operation.CLEAR)
  &&CurrentUser.require().permissions().contains(descriptor.managePermission());}
}
