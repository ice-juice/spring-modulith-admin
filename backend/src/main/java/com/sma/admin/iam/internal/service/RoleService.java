package com.sma.admin.iam.internal.service;

import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.*;
import com.sma.admin.iam.internal.persistence.*;

@Service
@Transactional(readOnly = true)
public class RoleService implements RoleAccessQuery {
    private final RoleMapper mapper;
    private final RoleDepartmentMapper departments;
    private final DepartmentService departmentService;
    private final StructureLockMapper structure;
    private final StructureGuard guard;
    private final List<OrganizationReference> references;
    private final ApplicationEventPublisher events;
    public RoleService(RoleMapper mapper, RoleDepartmentMapper departments, DepartmentService departmentService,
            StructureLockMapper structure, StructureGuard guard, List<OrganizationReference> references, ApplicationEventPublisher events) {
        this.mapper = mapper; this.departments = departments; this.departmentService = departmentService;
        this.structure = structure; this.guard = guard; this.references = references; this.events = events;
    }
    public record Input(Long parentId, @NotBlank @Size(max=128) String name,
            @NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_-]{0,63}") String code,
            @NotNull @Min(0) Integer sort, @NotNull Boolean enabled, @NotNull DataScope dataScope,
            @Size(max=200) Set<Long> departmentIds, @Size(max=512) String description) {}
    public record View(Long id, Long parentId, String portal, String name, String code, int sort,
            boolean enabled, DataScope dataScope, Set<Long> departmentIds, String description) {}

    public List<View> list() {
        var rows = mapper.selectList(inPortal().orderByAsc(RoleEntity::getSort).orderByAsc(RoleEntity::getId));
        var custom = departments.selectList(new LambdaQueryWrapper<>()).stream().collect(java.util.stream.Collectors.groupingBy(RoleDepartmentEntity::getRoleId));
        return rows.stream().map(row -> view(row, custom.getOrDefault(row.getId(),List.of()).stream().map(RoleDepartmentEntity::getDepartmentId).collect(java.util.stream.Collectors.toSet()))).toList();
    }
    public List<TreeNode> tree() { return Trees.build(list(), View::id, View::parentId, View::name, row -> !row.enabled()); }
    public List<OptionItem> options() { return list().stream().filter(View::enabled).map(row -> new OptionItem(row.id(),row.name(),false)).toList(); }
    public RoleEntity require(long id) {
        var row = mapper.selectOne(inPortal().eq(RoleEntity::getId,id));
        if (row == null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        return row;
    }
    @Transactional
    public View save(Long id, Input input) {
        guard.lock();
        var row = id == null ? new RoleEntity() : require(id);
        long parentId = input.parentId() == null ? 0 : input.parentId();
        Long cursor = parentId; Set<Long> seen = new HashSet<>();
        while (cursor != 0) {
            if (Objects.equals(id,cursor) || !seen.add(cursor)) throw new BizException(IamErrorCode.INVALID_PARENT);
            var parent = require(cursor);
            if (input.enabled() && !parent.getEnabled()) throw new BizException(IamErrorCode.DISABLED_PARENT);
            cursor = parent.getParentId();
        }
        Set<Long> custom = input.departmentIds() == null ? Set.of() : Set.copyOf(input.departmentIds());
        if (input.dataScope() == DataScope.CUSTOM && custom.isEmpty()) throw new BizException(CommonErrorCode.BAD_REQUEST);
        if (input.dataScope() != DataScope.CUSTOM) custom = Set.of();
        custom.forEach(departmentService::require);
        row.setParentId(parentId); row.setPortal(CurrentUser.portal()); row.setName(input.name().trim()); row.setCode(input.code().trim());
        row.setSort(input.sort()); row.setEnabled(input.enabled()); row.setDataScope(input.dataScope().name()); row.setDescription(input.description());
        try { if (id == null) mapper.insert(row); else mapper.updateById(row); }
        catch (DuplicateKeyException ex) { throw new BizException(IamErrorCode.DUPLICATE_CODE); }
        departments.delete(new LambdaQueryWrapper<RoleDepartmentEntity>().eq(RoleDepartmentEntity::getRoleId,row.getId()));
        for (Long departmentId : custom) {
            var relation = new RoleDepartmentEntity(); relation.setRoleId(row.getId()); relation.setDepartmentId(departmentId); departments.insert(relation);
        }
        events.publishEvent(new RoleChangedEvent(CurrentUser.tenantId(),row.getId(), id == null ? "create" : "update"));
        return view(row,custom);
    }
    @Transactional
    public void delete(long id) {
        guard.lock(); require(id);
        if (mapper.selectCount(inPortal().eq(RoleEntity::getParentId,id)) > 0) throw new BizException(IamErrorCode.HAS_CHILDREN);
        if (references.stream().anyMatch(ref -> ref.roleUsed(id))) throw new BizException(IamErrorCode.REFERENCED);
        mapper.deleteById(id); departments.delete(new LambdaQueryWrapper<RoleDepartmentEntity>().eq(RoleDepartmentEntity::getRoleId,id));
        events.publishEvent(new RoleChangedEvent(CurrentUser.tenantId(),id,"delete"));
    }
    @Override
    public DataAccess resolve(Set<Long> roleIds, Long departmentId) {
        if (roleIds.isEmpty()) return DataAccess.none();
        Set<Long> visibleDepartments = new HashSet<>(); boolean self = false;
        var rows = mapper.selectList(inPortal().in(RoleEntity::getId,roleIds).eq(RoleEntity::getEnabled,true));
        for (var row : rows) {
            switch (DataScope.valueOf(row.getDataScope())) {
                case ALL -> { return DataAccess.unrestricted(); }
                case SELF -> self = true;
                case DEPARTMENT -> { if (departmentId != null) visibleDepartments.add(departmentId); }
                case DEPARTMENT_TREE -> { if (departmentId != null) visibleDepartments.addAll(structure.descendantIds(departmentId)); }
                case CUSTOM -> departments.selectList(new LambdaQueryWrapper<RoleDepartmentEntity>().eq(RoleDepartmentEntity::getRoleId,row.getId()))
                        .forEach(relation -> visibleDepartments.add(relation.getDepartmentId()));
            }
        }
        return new DataAccess(false,visibleDepartments,self);
    }
    private LambdaQueryWrapper<RoleEntity> inPortal() { return new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getPortal,CurrentUser.portal()); }
    private View view(RoleEntity row, Set<Long> custom) {
        return new View(row.getId(),row.getParentId(),row.getPortal(),row.getName(),row.getCode(),row.getSort(),row.getEnabled(),DataScope.valueOf(row.getDataScope()),custom,row.getDescription());
    }
}
