package com.sma.admin.iam.internal.service;

import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.*;
import com.sma.admin.iam.internal.persistence.*;

@Service
@Transactional(readOnly = true)
public class DepartmentService {
    private final DepartmentMapper mapper;
    private final List<OrganizationReference> references;
    private final ApplicationEventPublisher events;
    private final StructureGuard guard;
    public DepartmentService(DepartmentMapper mapper, List<OrganizationReference> references, ApplicationEventPublisher events, StructureGuard guard) {
        this.mapper = mapper; this.references = references; this.events = events;
        this.guard = guard;
    }
    public record Input(Long parentId, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 128) String name,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,63}") String code,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Integer sort,
            @jakarta.validation.constraints.NotNull Boolean enabled) {}
    public record View(Long id, Long parentId, String name, String code, String path, int sort, boolean enabled) {}
    public List<View> list(String keyword) {
        var query = new LambdaQueryWrapper<DepartmentEntity>().orderByAsc(DepartmentEntity::getSort).orderByAsc(DepartmentEntity::getId);
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like(DepartmentEntity::getName, keyword).or().like(DepartmentEntity::getCode, keyword));
        return mapper.selectList(query).stream().map(this::view).toList();
    }
    public List<TreeNode> tree(boolean enabledOnly) {
        var rows = list(null).stream().filter(row -> !enabledOnly || row.enabled()).toList();
        return Trees.build(rows, View::id, View::parentId, View::name, row -> !row.enabled());
    }
    public DepartmentEntity require(long id) {
        var entity = mapper.selectById(id);
        if (entity == null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        return entity;
    }
    @Transactional
    public View save(Long id, Input input) {
        guard.lock();
        DepartmentEntity entity = id == null ? new DepartmentEntity() : require(id);
        if (id != null) guard.requireFullSubtree(entity.getPath(), mapper.selectCount(
                new LambdaQueryWrapper<DepartmentEntity>().likeRight(DepartmentEntity::getPath, entity.getPath())));
        long parentId = input.parentId() == null ? 0 : input.parentId();
        DepartmentEntity parent = parentId == 0 ? null : require(parentId);
        if (parent != null && id != null && parent.getPath().contains("/" + id + "/")) throw new BizException(IamErrorCode.INVALID_PARENT);
        if (parent != null && input.enabled() && !parent.getEnabled()) throw new BizException(IamErrorCode.DISABLED_PARENT);
        if (id != null && !input.enabled() && mapper.selectCount(new LambdaQueryWrapper<DepartmentEntity>()
                .likeRight(DepartmentEntity::getPath, entity.getPath()).ne(DepartmentEntity::getId, id).eq(DepartmentEntity::getEnabled, true)) > 0) {
            throw new BizException(IamErrorCode.HAS_CHILDREN);
        }
        String oldPath = entity.getPath();
        entity.setParentId(parentId); entity.setName(input.name().trim()); entity.setCode(input.code().trim());
        entity.setSort(input.sort()); entity.setEnabled(input.enabled());
        String prefix = parent == null ? "/" : parent.getPath();
        entity.setPath(prefix + (id == null ? "" : id + "/"));
        try {
            if (id == null) {
                mapper.insert(entity);
                entity.setPath(prefix + entity.getId() + "/"); mapper.updateById(entity);
            } else {
                mapper.updateById(entity);
                if (!oldPath.equals(entity.getPath())) {
                    var descendants = mapper.selectList(new LambdaQueryWrapper<DepartmentEntity>().likeRight(DepartmentEntity::getPath, oldPath)
                            .ne(DepartmentEntity::getId, id));
                    for (var child : descendants) {
                        child.setPath(entity.getPath() + child.getPath().substring(oldPath.length()));
                        mapper.updateById(child);
                    }
                }
            }
        } catch (DuplicateKeyException ex) { throw new BizException(IamErrorCode.DUPLICATE_CODE); }
        events.publishEvent(new DepartmentChangedEvent(CurrentUser.tenantId(), entity.getId(), id == null ? "create" : "update"));
        return view(entity);
    }
    @Transactional
    public void delete(long id) {
        guard.lock();
        var entity = require(id);
        guard.requireFullSubtree(entity.getPath(), mapper.selectCount(
                new LambdaQueryWrapper<DepartmentEntity>().likeRight(DepartmentEntity::getPath, entity.getPath())));
        if (mapper.selectCount(new LambdaQueryWrapper<DepartmentEntity>().eq(DepartmentEntity::getParentId, id)) > 0) throw new BizException(IamErrorCode.HAS_CHILDREN);
        if (references.stream().anyMatch(guard -> guard.departmentUsed(id))) throw new BizException(IamErrorCode.REFERENCED);
        mapper.deleteById(id);
        events.publishEvent(new DepartmentChangedEvent(CurrentUser.tenantId(), id, "delete"));
    }
    private View view(DepartmentEntity entity) {
        return new View(entity.getId(), entity.getParentId(), entity.getName(), entity.getCode(), entity.getPath(), entity.getSort(), entity.getEnabled());
    }
}
