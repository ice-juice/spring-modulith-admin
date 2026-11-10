package com.sma.admin.config.internal.service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class ParameterService implements ParameterQuery {
 private final ParameterMapper mapper;private final ParameterGroupMapper groups;private final ConfigNames names;private final WriteLockMapper lock;
 private final ApplicationEventPublisher events;private final JsonMapper json;
 private record CacheKey(long tenant,String key) {}
 private record Cached(Optional<String> value,long generation,Instant until) {}
 private final ConcurrentHashMap<CacheKey,Cached> cache=new ConcurrentHashMap<>();
 private final AtomicLong generation=new AtomicLong();
 public ParameterService(ParameterMapper mapper,ParameterGroupMapper groups,ConfigNames names,WriteLockMapper lock,ApplicationEventPublisher events,JsonMapper json) {
  this.mapper=mapper;this.groups=groups;this.names=names;this.lock=lock;this.events=events;this.json=json;
 }
 public record GroupInput(@NotBlank @Pattern(regexp="[a-z][a-z0-9_-]{0,63}") String groupKey,@NotBlank @Size(max=128) String name,@Size(max=256) String nameKey,@NotNull @Min(0) Integer sort,Map<String,String> translations) {}
 public record GroupView(Long id,String groupKey,String name,String defaultName,String nameKey,int sort,boolean builtin,Map<String,String> translations) {}
 public record Input(@NotBlank @Pattern(regexp="[a-z][a-zA-Z0-9.-]{0,127}") String paramKey,@NotBlank @Size(max=64) String groupKey,
  @NotBlank @Size(max=128) String name,@Size(max=256) String nameKey,@NotBlank @Pattern(regexp="STRING|BOOLEAN|INTEGER|DECIMAL|JSON") String valueType,
  @Size(max=4000) String value,@NotNull Boolean publicReadable,@NotNull Boolean secret,@NotNull @Min(0) Integer sort,Map<String,String> translations) {}
 public record View(Long id,String paramKey,String groupKey,String name,String defaultName,String nameKey,String valueType,String value,boolean publicReadable,boolean secret,int sort,boolean builtin,Map<String,String> translations) {}
 public List<GroupView> groups() { return groups.selectList(new LambdaQueryWrapper<ParameterGroupEntity>().orderByAsc(ParameterGroupEntity::getSort).orderByAsc(ParameterGroupEntity::getId)).stream().map(this::groupView).toList(); }
 public List<View> list(String group,String keyword) {
  var query=new LambdaQueryWrapper<ParameterEntity>().orderByAsc(ParameterEntity::getSort).orderByAsc(ParameterEntity::getId);
  if(group!=null&&!group.isBlank()) query.eq(ParameterEntity::getGroupKey,group);
  if(keyword!=null&&!keyword.isBlank()) query.and(q -> q.like(ParameterEntity::getName,keyword).or().like(ParameterEntity::getParamKey,keyword));
  return mapper.selectList(query).stream().map(this::view).toList();
 }
 private ParameterEntity require(long id) { var row=mapper.selectById(id);if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row; }
 @Transactional public GroupView saveGroup(Long id,GroupInput input) {
  lock.lock(CurrentUser.tenantId());var row=id==null?new ParameterGroupEntity():groups.selectById(id);
  if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
  if(id!=null&&!row.getGroupKey().equals(input.groupKey()) && (row.getBuiltin()||mapper.selectCount(new LambdaQueryWrapper<ParameterEntity>().eq(ParameterEntity::getGroupKey,row.getGroupKey()))>0)) throw new BizException(ConfigErrorCode.BUILTIN);
  row.setGroupKey(input.groupKey());row.setName(input.name());row.setNameKey(input.nameKey());row.setSort(input.sort());if(id==null) row.setBuiltin(false);
  try { if(id==null)groups.insert(row);else groups.updateById(row); } catch(DuplicateKeyException ex){throw new BizException(ConfigErrorCode.DUPLICATE);}
  names.replace("parameter-group",row.getId(),input.translations());return groupView(row);
 }
 @Transactional public void deleteGroup(long id) {
  lock.lock(CurrentUser.tenantId());var row=groups.selectById(id);if(row==null)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
  if(row.getBuiltin()||mapper.selectCount(new LambdaQueryWrapper<ParameterEntity>().eq(ParameterEntity::getGroupKey,row.getGroupKey()))>0)throw new BizException(ConfigErrorCode.BUILTIN);
  groups.deleteById(id);names.replace("parameter-group",id,Map.of());
 }
 @Transactional public View save(Long id,Input input) {
  lock.lock(CurrentUser.tenantId());var row=id==null?new ParameterEntity():require(id);
  if(Boolean.TRUE.equals(row.getBuiltin())&&(!row.getParamKey().equals(input.paramKey())||!row.getValueType().equals(input.valueType())))throw new BizException(ConfigErrorCode.BUILTIN);
  if(groups.selectCount(new LambdaQueryWrapper<ParameterGroupEntity>().eq(ParameterGroupEntity::getGroupKey,input.groupKey()))==0)throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
  String value=input.value()==null&&id!=null&&row.getSecret()?row.getParamValue():input.value();
  validate(input.paramKey(),input.valueType(),value);
  if(input.secret()&&input.publicReadable())throw new BizException(ConfigErrorCode.INVALID_VALUE);
  row.setParamKey(input.paramKey());row.setGroupKey(input.groupKey());row.setName(input.name());row.setNameKey(input.nameKey());row.setValueType(input.valueType());row.setParamValue(value);
  row.setPublicReadable(input.publicReadable());row.setSecret(input.secret());row.setSort(input.sort());if(id==null)row.setBuiltin(false);
  try {if(id==null)mapper.insert(row);else mapper.updateById(row);}catch(DuplicateKeyException ex){throw new BizException(ConfigErrorCode.DUPLICATE);}
  names.replace("parameter",row.getId(),input.translations());changed(row.getParamKey());return view(row);
 }
 @Transactional public void delete(long id) {
  lock.lock(CurrentUser.tenantId());var row=require(id);if(row.getBuiltin())throw new BizException(ConfigErrorCode.BUILTIN);
  mapper.deleteById(id);names.replace("parameter",id,Map.of());changed(row.getParamKey());
 }
 private void validate(String key,String type,String value) {
  if(value==null)throw new BizException(ConfigErrorCode.INVALID_VALUE);
  try {
   switch(type) {
    case "BOOLEAN" -> {if(!Set.of("true","false").contains(value))throw new IllegalArgumentException();}
    case "INTEGER" -> Long.parseLong(value);
    case "DECIMAL" -> new BigDecimal(value);
    case "JSON" -> {if(json.readTree(value)==null)throw new IllegalArgumentException();}
    case "STRING" -> {}
    default -> throw new IllegalArgumentException();
   }
   if(Set.of("auth.sessionTtlSeconds","auth.rememberTtlSeconds").contains(key)&&(Long.parseLong(value)<60||Long.parseLong(value)>31536000))throw new IllegalArgumentException();
   if("auth.autoLockSeconds".equals(key)&&(Long.parseLong(value)<0||Long.parseLong(value)>86400))throw new IllegalArgumentException();
   if("auth.passwordNoticeDays".equals(key)&&(Long.parseLong(value)<0||Long.parseLong(value)>3650))throw new IllegalArgumentException();
  }catch(Exception ex){throw new BizException(ConfigErrorCode.INVALID_VALUE);}
 }
 @Override public String stringValue(String key,String fallback) {
  var cacheKey=new CacheKey(CurrentUser.tenantId(),key);long revision=generation.get();var entry=cache.get(cacheKey);
  if(entry!=null&&entry.generation()==revision&&entry.until().isAfter(Instant.now()))return entry.value().orElse(fallback);
  var row=mapper.selectOne(new LambdaQueryWrapper<ParameterEntity>().eq(ParameterEntity::getParamKey,key));
  var result=Optional.ofNullable(row==null?null:row.getParamValue());
  if(cache.size()>10000)cache.clear();
  cache.put(cacheKey,new Cached(result,revision,Instant.now().plusSeconds(300)));return result.orElse(fallback);
 }
 @Override public boolean booleanValue(String key,boolean fallback){return Boolean.parseBoolean(stringValue(key,Boolean.toString(fallback)));}
 @Override public long longValue(String key,long fallback){return Long.parseLong(stringValue(key,Long.toString(fallback)));}
 @Override public BigDecimal decimalValue(String key,BigDecimal fallback){return new BigDecimal(stringValue(key,fallback.toPlainString()));}
 @Override public Map<String,String> publicValues(){
  Map<String,String> values=new LinkedHashMap<>();
  mapper.selectList(new LambdaQueryWrapper<ParameterEntity>().eq(ParameterEntity::getPublicReadable,true).eq(ParameterEntity::getSecret,false)).forEach(row -> values.put(row.getParamKey(),row.getParamValue()));return Map.copyOf(values);
 }
 public void refresh(){generation.incrementAndGet();cache.clear();}
 private void changed(String key){
  events.publishEvent(new ConfigurationChangedEvent(CurrentUser.tenantId(),"parameter",key));
  if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){refresh();}});
  else refresh();
 }
 private GroupView groupView(ParameterGroupEntity row){var texts=names.translations("parameter-group",row.getId());return new GroupView(row.getId(),row.getGroupKey(),names.name(row.getName(),row.getNameKey(),texts),row.getName(),row.getNameKey(),row.getSort(),row.getBuiltin(),texts);}
 private View view(ParameterEntity row){var texts=names.translations("parameter",row.getId());return new View(row.getId(),row.getParamKey(),row.getGroupKey(),names.name(row.getName(),row.getNameKey(),texts),row.getName(),row.getNameKey(),row.getValueType(),row.getSecret()?null:row.getParamValue(),row.getPublicReadable(),row.getSecret(),row.getSort(),row.getBuiltin(),texts);}
 public record SeedGroup(String key,String name,String nameKey){}
 public record Seed(String key,String group,String name,String nameKey,String type,String value,boolean publicly){}
 @Transactional public void initialize(List<SeedGroup> groupSpecs,List<Seed> specs){
  lock.lock(CurrentUser.tenantId());
  for(var spec:groupSpecs)if(groups.selectCount(new LambdaQueryWrapper<ParameterGroupEntity>().eq(ParameterGroupEntity::getGroupKey,spec.key()))==0){
   var view=saveGroup(null,new GroupInput(spec.key(),spec.name(),spec.nameKey(),0,Map.of()));var row=groups.selectById(view.id());row.setBuiltin(true);groups.updateById(row);
  }
  for(var spec:specs)if(mapper.selectCount(new LambdaQueryWrapper<ParameterEntity>().eq(ParameterEntity::getParamKey,spec.key()))==0){
   var view=save(null,new Input(spec.key(),spec.group(),spec.name(),spec.nameKey(),spec.type(),spec.value(),spec.publicly(),false,0,Map.of()));var row=require(view.id());row.setBuiltin(true);mapper.updateById(row);
  }
 }
}
