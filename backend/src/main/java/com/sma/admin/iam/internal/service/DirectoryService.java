package com.sma.admin.iam.internal.service;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.OptionItem;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.DirectoryQuery;
import com.sma.admin.iam.internal.persistence.*;
@Service
public class DirectoryService implements DirectoryQuery {
 private final AccountMapper accounts;public DirectoryService(AccountMapper accounts){this.accounts=accounts;}
 public java.util.List<OptionItem> search(String keyword){
  var query=new LambdaQueryWrapper<UserEntity>().select(UserEntity::getId,UserEntity::getName).eq(UserEntity::getPortal,CurrentUser.portal()).eq(UserEntity::getEnabled,true);
  if(keyword!=null&&!keyword.isBlank())query.and(q->q.like(UserEntity::getName,keyword).or().like(UserEntity::getUsername,keyword));
  return accounts.selectList(query.orderByAsc(UserEntity::getName).last("LIMIT 200")).stream().map(row->new OptionItem(row.getId(),row.getName(),false)).toList();
 }
 public java.util.Map<Long,String> names(java.util.Set<Long> ids){if(ids.isEmpty())return java.util.Map.of();if(ids.size()>200)throw new com.sma.admin.common.BizException(com.sma.admin.common.CommonErrorCode.BAD_REQUEST);
  return accounts.selectList(new LambdaQueryWrapper<UserEntity>().select(UserEntity::getId,UserEntity::getName).in(UserEntity::getId,ids).eq(UserEntity::getPortal,CurrentUser.portal()))
   .stream().collect(java.util.stream.Collectors.toUnmodifiableMap(UserEntity::getId,UserEntity::getName));}
}
