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
}
