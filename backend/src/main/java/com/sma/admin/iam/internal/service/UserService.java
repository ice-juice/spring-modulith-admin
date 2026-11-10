package com.sma.admin.iam.internal.service;

import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.*;
import com.sma.admin.iam.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class UserService implements AccountQuery {
    private final UserMapper mapper;
    private final AccountMapper accounts;
    private final UserRoleMapper userRoles;
    private final UserPostMapper userPosts;
    private final DepartmentService departments;
    private final RoleService roles;
    private final PostService posts;
    private final RolePermissionService grants;
    private final StructureGuard guard;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    public UserService(UserMapper mapper,AccountMapper accounts,UserRoleMapper userRoles,UserPostMapper userPosts,
            DepartmentService departments,RoleService roles,PostService posts,RolePermissionService grants,
            StructureGuard guard,PasswordEncoder passwords,ApplicationEventPublisher events) {
        this.mapper=mapper; this.accounts=accounts; this.userRoles=userRoles; this.userPosts=userPosts;
        this.departments=departments; this.roles=roles; this.posts=posts; this.grants=grants; this.guard=guard;
        this.passwords=passwords; this.events=events;
    }
    public record Input(@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_.@-]{1,63}") String username,
            @NotBlank @Size(max=128) String name,Long departmentId,@Email @Size(max=254) String email,
            @Size(max=32) String mobile,@Pattern(regexp="MALE|FEMALE|UNSPECIFIED") String gender,
            @NotNull Boolean enabled,@NotNull @Size(max=100) Set<@NotNull Long> roleIds,
            @NotNull @Size(max=100) Set<@NotNull Long> postIds,@Size(max=16) String locale,@Size(max=64) String timeZone) {}
    public record Create(@jakarta.validation.Valid @NotNull Input user,@NotBlank String password) {}
    public record View(Long id,String username,String name,String portal,Long departmentId,String email,String mobile,String gender,
            boolean enabled,boolean superAdmin,Set<Long> roleIds,Set<Long> postIds,String locale,String timeZone,Instant passwordChangedAt) {}

    public PageResult<View> page(PageQuery page,String keyword,Long departmentId,Boolean enabled) {
        var query=new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getPortal,CurrentUser.portal()).orderByDesc(UserEntity::getId);
        if(keyword!=null&&!keyword.isBlank()) query.and(q -> q.like(UserEntity::getUsername,keyword).or().like(UserEntity::getName,keyword));
        if(departmentId!=null) query.eq(UserEntity::getDeptId,departmentId);
        if(enabled!=null) query.eq(UserEntity::getEnabled,enabled);
        return PageResult.of(mapper.selectPage(page.toPage(Map.of("username","username","name","name","createdAt","created_at")),query),this::view);
    }
    public View get(long id) { return view(require(id)); }
    private UserEntity require(long id) {
        var row=mapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getId,id).eq(UserEntity::getPortal,CurrentUser.portal()));
        if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        return row;
    }
    @Transactional
    public View create(Create input) { return save(null,input.user(),input.password()); }
    @Transactional
    public View update(long id,Input input) { return save(id,input,null); }
    private View save(Long id,Input input,String password) {
        guard.lock();
        var row=id==null?new UserEntity():require(id);
        if(id!=null) protect(row);
        validate(input);
        row.setUsername(input.username()); row.setName(input.name().trim()); row.setPortal(CurrentUser.portal()); row.setDeptId(input.departmentId());
        row.setEmail(input.email()); row.setMobile(input.mobile()); row.setGender(input.gender()); row.setEnabled(input.enabled());
        row.setLocale(input.locale()); row.setTimeZone(input.timeZone());
        if(id==null) {
            row.setSuperAdmin(false); row.setPasswordHash(hash(password)); row.setPasswordChangedAt(Instant.now()); row.setCredentialVersion(0L);
        }
        else row.setCredentialVersion(row.getCredentialVersion()+1);
        try { if(id==null) mapper.insert(row); else mapper.updateById(row); }
        catch(DuplicateKeyException ex) { throw new BizException(IamErrorCode.DUPLICATE_CODE); }
        userRoles.delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId,row.getId()));
        for(long roleId:input.roleIds()) { var relation=new UserRoleEntity(); relation.setUserId(row.getId()); relation.setRoleId(roleId); userRoles.insert(relation); }
        userPosts.delete(new LambdaQueryWrapper<UserPostEntity>().eq(UserPostEntity::getUserId,row.getId()));
        for(long postId:input.postIds()) { var relation=new UserPostEntity(); relation.setUserId(row.getId()); relation.setPostId(postId); userPosts.insert(relation); }
        events.publishEvent(new UserChangedEvent(CurrentUser.tenantId(),row.getId(),id==null?"create":"update"));
        return view(row);
    }
    private void validate(Input input) {
        if(input.timeZone()!=null&&!input.timeZone().isBlank()) try { ZoneId.of(input.timeZone()); } catch(DateTimeException ex) { throw new BizException(CommonErrorCode.BAD_REQUEST); }
        if(input.locale()!=null&&!input.locale().isBlank()&&!Set.of("zh-CN","en-US").contains(input.locale())) throw new BizException(CommonErrorCode.BAD_REQUEST);
        if(input.departmentId()!=null&&!departments.require(input.departmentId()).getEnabled()) throw new BizException(CommonErrorCode.BAD_REQUEST);
        for(long roleId:input.roleIds()) if(!roles.require(roleId).getEnabled()) throw new BizException(CommonErrorCode.BAD_REQUEST);
        for(long postId:input.postIds()) if(!posts.require(postId).getEnabled()) throw new BizException(CommonErrorCode.BAD_REQUEST);
        CurrentUser.optional().filter(actor -> !actor.superAdmin()).ifPresent(actor -> {
            if(!actor.dataAccess().all() && (input.departmentId()==null || !actor.dataAccess().departmentIds().contains(input.departmentId()))) {
                throw new AccessDeniedException("Target department is outside your data scope");
            }
            if(!actor.permissions().containsAll(grants.keys(input.roleIds(),false))) throw new AccessDeniedException("Role permissions exceed grantor permissions");
            DataAccess proposed=roles.resolve(input.roleIds(),input.departmentId());
            if(!actor.dataAccess().all() && (proposed.all()||!actor.dataAccess().departmentIds().containsAll(proposed.departmentIds()))) throw new AccessDeniedException("Role data scope exceeds grantor data scope");
        });
    }
    @Transactional
    public void delete(long id) {
        guard.lock(); var row=require(id); protect(row);
        mapper.deleteById(id);
        userRoles.delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId,id));
        userPosts.delete(new LambdaQueryWrapper<UserPostEntity>().eq(UserPostEntity::getUserId,id));
        events.publishEvent(new UserChangedEvent(CurrentUser.tenantId(),id,"delete"));
    }
    @Transactional
    public void resetPassword(long id,String password) {
        guard.lock(); var row=require(id); protect(row);
        row.setPasswordHash(hash(password)); row.setPasswordChangedAt(Instant.now()); row.setCredentialVersion(row.getCredentialVersion()+1); mapper.updateById(row);
        events.publishEvent(new UserChangedEvent(CurrentUser.tenantId(),id,"password"));
    }
    @Transactional
    public void changeOwnPassword(String oldPassword,String newPassword) {
        long id=CurrentUser.require().userId();
        guard.lock(); var row=accounts.selectById(id);
        if(row==null||!passwords.matches(oldPassword,row.getPasswordHash())) throw new BizException(IamErrorCode.INVALID_PASSWORD);
        row.setPasswordHash(hash(newPassword)); row.setPasswordChangedAt(Instant.now()); row.setCredentialVersion(row.getCredentialVersion()+1); accounts.updateById(row);
        events.publishEvent(new UserChangedEvent(CurrentUser.tenantId(),id,"password"));
    }
    private void protect(UserEntity row) {
        if(row.getSuperAdmin()||row.getId()==CurrentUser.userId()) throw new BizException(IamErrorCode.PROTECTED_USER);
    }
    private String hash(String password) {
        if(password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72) throw new BizException(IamErrorCode.WEAK_PASSWORD);
        return passwords.encode(password);
    }
    @Override public Optional<AccountView> authenticate(String username,String password,String portal) {
        var row=accounts.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername,username).eq(UserEntity::getPortal,portal));
        if(row==null||!row.getEnabled()||!passwords.matches(password,row.getPasswordHash())) return Optional.empty();
        return Optional.of(account(row));
    }
    @Override public Optional<AccountView> findById(long id) { return Optional.ofNullable(accounts.selectById(id)).map(this::account); }
    @Override public boolean matchesPassword(long id,String password) {
        var row=accounts.selectById(id); return row!=null&&row.getEnabled()&&passwords.matches(password,row.getPasswordHash());
    }
    private View view(UserEntity row) {
        var account=account(row);
        return new View(row.getId(),row.getUsername(),row.getName(),row.getPortal(),row.getDeptId(),row.getEmail(),row.getMobile(),row.getGender(),row.getEnabled(),
                row.getSuperAdmin(),account.roleIds(),account.postIds(),row.getLocale(),row.getTimeZone(),row.getPasswordChangedAt());
    }
    private AccountView account(UserEntity row) {
        var roleIds=userRoles.selectList(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId,row.getId())).stream().map(UserRoleEntity::getRoleId).collect(java.util.stream.Collectors.toSet());
        var postIds=userPosts.selectList(new LambdaQueryWrapper<UserPostEntity>().eq(UserPostEntity::getUserId,row.getId())).stream().map(UserPostEntity::getPostId).collect(java.util.stream.Collectors.toSet());
        return new AccountView(row.getId(),row.getTenantId(),row.getUsername(),row.getName(),row.getPortal(),row.getDeptId(),row.getSuperAdmin(),row.getEnabled(),roleIds,postIds,row.getLocale(),row.getTimeZone(),row.getPasswordChangedAt(),row.getCredentialVersion());
    }

    @Transactional
    public void bootstrap(String password) {
        guard.lock();
        if(accounts.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getPortal,"admin").eq(UserEntity::getSuperAdmin,true))>0) return;
        if(password==null||password.isBlank()) return;
        var row=new UserEntity(); row.setUsername("admin"); row.setName("Administrator"); row.setPortal("admin");
        row.setPasswordHash(hash(password)); row.setEnabled(true); row.setSuperAdmin(true); row.setPasswordChangedAt(Instant.now()); row.setCredentialVersion(0L); accounts.insert(row);
    }
}
