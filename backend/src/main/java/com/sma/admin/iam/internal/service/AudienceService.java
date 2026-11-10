package com.sma.admin.iam.internal.service;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.AudienceQuery;
import com.sma.admin.iam.internal.persistence.*;

@Service
public class AudienceService implements AudienceQuery {
 private final UserMapper users;private final UserRoleMapper userRoles;private final UserPostMapper userPosts;
 private final RoleMapper roles;private final DepartmentMapper departments;private final PostMapper posts;
 public AudienceService(UserMapper users,UserRoleMapper userRoles,UserPostMapper userPosts,RoleMapper roles,DepartmentMapper departments,PostMapper posts){
  this.users=users;this.userRoles=userRoles;this.userPosts=userPosts;this.roles=roles;this.departments=departments;this.posts=posts;
 }
 @Override public Map<Long,String> resolve(String type,Set<Long> ids){
  if(type==null||ids==null||ids.size()>1000||ids.stream().anyMatch(Objects::isNull))throw new BizException(CommonErrorCode.BAD_REQUEST);
  if(!"ALL".equals(type)&&ids.isEmpty())throw new BizException(CommonErrorCode.BAD_REQUEST);
  var query=new LambdaQueryWrapper<UserEntity>().select(UserEntity::getId,UserEntity::getName).eq(UserEntity::getPortal,CurrentUser.portal()).eq(UserEntity::getEnabled,true);
  switch(type){
   case "ALL"->{if(!ids.isEmpty())throw new BizException(CommonErrorCode.BAD_REQUEST);}
   case "USER"->query.in(UserEntity::getId,ids);
   case "DEPARTMENT"->{
    if(departments.selectCount(new LambdaQueryWrapper<DepartmentEntity>().in(DepartmentEntity::getId,ids).eq(DepartmentEntity::getEnabled,true))!=ids.size())throw new BizException(CommonErrorCode.BAD_REQUEST);
    query.in(UserEntity::getDeptId,ids);
   }
   case "ROLE"->{
    if(roles.selectCount(new LambdaQueryWrapper<RoleEntity>().in(RoleEntity::getId,ids).eq(RoleEntity::getEnabled,true).eq(RoleEntity::getPortal,CurrentUser.portal()))!=ids.size())throw new BizException(CommonErrorCode.BAD_REQUEST);
    var members=userRoles.selectList(new LambdaQueryWrapper<UserRoleEntity>().in(UserRoleEntity::getRoleId,ids)).stream().map(UserRoleEntity::getUserId).collect(Collectors.toSet());
    if(members.isEmpty())return Map.of();query.in(UserEntity::getId,members);
   }
   case "POST"->{
    if(posts.selectCount(new LambdaQueryWrapper<PostEntity>().in(PostEntity::getId,ids).eq(PostEntity::getEnabled,true))!=ids.size())throw new BizException(CommonErrorCode.BAD_REQUEST);
    var members=userPosts.selectList(new LambdaQueryWrapper<UserPostEntity>().in(UserPostEntity::getPostId,ids)).stream().map(UserPostEntity::getUserId).collect(Collectors.toSet());
    if(members.isEmpty())return Map.of();query.in(UserEntity::getId,members);
   }
   default->throw new BizException(CommonErrorCode.BAD_REQUEST);
  }
  var result=users.selectList(query.last("LIMIT 10001")).stream().collect(Collectors.toMap(UserEntity::getId,UserEntity::getName));
  if(result.size()>10000||("USER".equals(type)&&result.size()!=ids.size()))throw new BizException(CommonErrorCode.BAD_REQUEST);
  return Map.copyOf(result);
 }
 @Override public List<OptionItem> options(String type,String keyword){
  String search=keyword==null?"":keyword.trim();
  return switch(type){
   case "USER"->users.selectList(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getPortal,CurrentUser.portal()).eq(UserEntity::getEnabled,true)
    .and(!search.isBlank(),q->q.like(UserEntity::getUsername,search).or().like(UserEntity::getName,search)).orderByAsc(UserEntity::getName).last("LIMIT 200"))
    .stream().map(row->new OptionItem(row.getId(),row.getName(),false)).toList();
   case "ROLE"->roles.selectList(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getPortal,CurrentUser.portal()).eq(RoleEntity::getEnabled,true)
    .like(!search.isBlank(),RoleEntity::getName,search).orderByAsc(RoleEntity::getSort).last("LIMIT 200")).stream().map(row->new OptionItem(row.getId(),row.getName(),false)).toList();
   case "DEPARTMENT"->departments.selectList(new LambdaQueryWrapper<DepartmentEntity>().eq(DepartmentEntity::getEnabled,true).like(!search.isBlank(),DepartmentEntity::getName,search)
    .orderByAsc(DepartmentEntity::getSort).last("LIMIT 200")).stream().map(row->new OptionItem(row.getId(),row.getName(),false)).toList();
   case "POST"->posts.selectList(new LambdaQueryWrapper<PostEntity>().eq(PostEntity::getEnabled,true).like(!search.isBlank(),PostEntity::getName,search)
    .orderByAsc(PostEntity::getSort).last("LIMIT 200")).stream().map(row->new OptionItem(row.getId(),row.getName(),false)).toList();
   default->throw new BizException(CommonErrorCode.BAD_REQUEST);
  };
 }
}
