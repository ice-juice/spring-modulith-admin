package com.sma.admin.iam.internal.service;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.OptionItem;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.DirectoryQuery;
import com.sma.admin.iam.internal.persistence.*;
@Service
public class DirectoryService implements DirectoryQuery {
 private final AccountMapper accounts;private final IamDisplayCaches cache;
 public DirectoryService(AccountMapper accounts,IamDisplayCaches cache){this.accounts=accounts;this.cache=cache;}
 public record OptionsSnapshot(java.util.List<OptionItem> rows){public OptionsSnapshot{rows=java.util.List.copyOf(rows);}}
 public record NamesSnapshot(java.util.Map<Long,String> names){public NamesSnapshot{names=java.util.Map.copyOf(names);}}
 public java.util.List<OptionItem> search(String keyword){
  String term=keyword==null||keyword.isBlank()?"":keyword;
  return cache.get(IamDisplayCaches.USERS,"search:"+term,OptionsSnapshot.class,()->new OptionsSnapshot(loadSearch(keyword))).rows();
 }
 private java.util.List<OptionItem> loadSearch(String keyword){
  var query=new LambdaQueryWrapper<UserEntity>().select(UserEntity::getId,UserEntity::getName).eq(UserEntity::getPortal,CurrentUser.portal()).eq(UserEntity::getEnabled,true);
  if(keyword!=null&&!keyword.isBlank())query.and(q->q.like(UserEntity::getName,keyword).or().like(UserEntity::getUsername,keyword));
  return accounts.selectList(query.orderByAsc(UserEntity::getName).last("LIMIT 200")).stream().map(row->new OptionItem(row.getId(),row.getName(),false)).toList();
 }
 public java.util.Map<Long,String> names(java.util.Set<Long> ids){if(ids.isEmpty())return java.util.Map.of();if(ids.size()>200)throw new com.sma.admin.common.BizException(com.sma.admin.common.CommonErrorCode.BAD_REQUEST);
  var selected=java.util.Set.copyOf(ids);
  return cache.get(IamDisplayCaches.USERS,"names:"+selected.stream().sorted().toList(),NamesSnapshot.class,()->new NamesSnapshot(loadNames(selected))).names();
 }
 private java.util.Map<Long,String> loadNames(java.util.Set<Long> ids){
  return accounts.selectList(new LambdaQueryWrapper<UserEntity>().select(UserEntity::getId,UserEntity::getName).in(UserEntity::getId,ids).eq(UserEntity::getPortal,CurrentUser.portal()))
   .stream().collect(java.util.stream.Collectors.toUnmodifiableMap(UserEntity::getId,UserEntity::getName));}
}
