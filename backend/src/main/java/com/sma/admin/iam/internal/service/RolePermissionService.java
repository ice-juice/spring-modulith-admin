package com.sma.admin.iam.internal.service;

import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.*;
import com.sma.admin.iam.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class RolePermissionService implements PermissionQuery {
    private final RolePermissionMapper mapper;
    private final RoleMapper roleMapper;
    private final RoleService roles;
    private final PermissionService permissions;
    private final StructureGuard guard;
    private final ApplicationEventPublisher events;
    public RolePermissionService(RolePermissionMapper mapper,RoleMapper roleMapper,RoleService roles,PermissionService permissions,StructureGuard guard,ApplicationEventPublisher events) {
        this.mapper=mapper; this.roleMapper=roleMapper; this.roles=roles; this.permissions=permissions; this.guard=guard; this.events=events;
    }
    public Set<Long> selected(long roleId) {
        roles.require(roleId);
        return mapper.selectList(new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId,roleId))
                .stream().map(RolePermissionEntity::getPermissionId).collect(java.util.stream.Collectors.toSet());
    }
    @Transactional
    public void replace(long roleId,Set<Long> selected) {
        guard.lock(); roles.require(roleId);
        if(selected==null||selected.size()>2000) throw new BizException(CommonErrorCode.BAD_REQUEST);
        Map<Long,PermissionEntity> catalog=new HashMap<>();
        permissions.entities().forEach(row -> catalog.put(row.getId(),row));
        Set<Long> expanded=new HashSet<>(selected);
        for(Long id:selected) {
            var node=catalog.get(id);
            if(node==null||!node.getEnabled()) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
            Set<Long> visited=new HashSet<>();
            while(node!=null) {
                if(!visited.add(node.getId())||!node.getEnabled()) throw new BizException(CommonErrorCode.BAD_REQUEST);
                expanded.add(node.getId());
                node=catalog.get(node.getParentId());
            }
        }
        CurrentUser.optional().ifPresent(actor -> {
            if(!actor.superAdmin() && expanded.stream().map(catalog::get).anyMatch(row -> !actor.permissions().contains(row.getPermissionKey()))) {
                throw new AccessDeniedException("Cannot grant permissions beyond your own");
            }
        });
        mapper.delete(new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId,roleId));
        for(Long id:expanded) {
            var relation=new RolePermissionEntity(); relation.setRoleId(roleId); relation.setPermissionId(id); mapper.insert(relation);
        }
        events.publishEvent(new PermissionChangedEvent(CurrentUser.tenantId(),CurrentUser.portal(),roleId,"grant"));
    }
    @Override
    public Set<String> keys(Set<Long> roleIds,boolean superAdmin) {
        return granted(roleIds,superAdmin).stream().map(PermissionEntity::getPermissionKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    @Override
    public List<MenuView> menus(Set<Long> roleIds,boolean superAdmin) {
        Set<Long> visible=granted(roleIds,superAdmin).stream().map(PermissionEntity::getId).collect(java.util.stream.Collectors.toSet());
        Map<Long,List<PermissionService.View>> children=new HashMap<>();
        permissions.list().stream().filter(row -> visible.contains(row.id())&&!row.hidden()&&Set.of("DIRECTORY","MENU").contains(row.type()))
                .forEach(row -> children.computeIfAbsent(row.parentId(),key -> new ArrayList<>()).add(row));
        return children.getOrDefault(0L,List.of()).stream().map(row -> menu(row,children)).filter(Objects::nonNull).toList();
    }
    @Override
    public Map<String,String> icons(Set<Long> roleIds,boolean superAdmin) {
        return granted(roleIds,superAdmin).stream()
                .filter(row -> row.getIcon()!=null&&!row.getIcon().isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(PermissionEntity::getPermissionKey,PermissionEntity::getIcon));
    }
    private List<PermissionEntity> granted(Set<Long> roleIds,boolean superAdmin) {
        var catalog=permissions.entities();
        Map<Long,PermissionEntity> indexed=new HashMap<>(); catalog.forEach(row -> indexed.put(row.getId(),row));
        Set<Long> activeRoles=roleIds.isEmpty()?Set.of():roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .in(RoleEntity::getId,roleIds).eq(RoleEntity::getPortal,CurrentUser.portal()).eq(RoleEntity::getEnabled,true))
                .stream().map(RoleEntity::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> ids=activeRoles.isEmpty()?Set.of():mapper.selectList(new LambdaQueryWrapper<RolePermissionEntity>().in(RolePermissionEntity::getRoleId,activeRoles))
                .stream().map(RolePermissionEntity::getPermissionId).collect(java.util.stream.Collectors.toSet());
        return catalog.stream().filter(row -> superAdmin?row.getSuperAdminDefault():ids.contains(row.getId()))
                .filter(row -> enabledChain(row,indexed)).toList();
    }
    private boolean enabledChain(PermissionEntity node,Map<Long,PermissionEntity> indexed) {
        Set<Long> visited=new HashSet<>();
        while(node!=null) {
            if(!node.getEnabled()||!visited.add(node.getId())) return false;
            if(node.getParentId()==0) return true;
            node=indexed.get(node.getParentId());
        }
        return false;
    }
    private MenuView menu(PermissionService.View row,Map<Long,List<PermissionService.View>> children) {
        var nested=children.getOrDefault(row.id(),List.of()).stream().map(child -> menu(child,children)).filter(Objects::nonNull).toList();
        if ("DIRECTORY".equals(row.type()) && nested.isEmpty()) return null;
        return new MenuView(row.id(),row.parentId(),row.name(),row.path(),row.icon(),row.permissionKey(),row.type(),row.openMode(),
                nested);
    }
}
