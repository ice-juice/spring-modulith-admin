package com.sma.admin.iam.internal.service;

import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.iam.IamErrorCode;
import com.sma.admin.iam.internal.persistence.*;

@Service
@Transactional(readOnly = true)
public class PostService {
    private final PostMapper mapper;
    private final List<OrganizationReference> references;
    private final StructureGuard guard;
    public PostService(PostMapper mapper, List<OrganizationReference> references, StructureGuard guard) {
        this.mapper = mapper; this.references = references; this.guard = guard;
    }
    public record Input(@NotBlank @Size(max = 128) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,63}") String code,
            @NotNull @Min(0) Integer sort, @NotNull Boolean enabled, @Size(max = 512) String description) {}
    public record View(Long id, String name, String code, int sort, boolean enabled, String description) {}
    public PageResult<View> page(PageQuery page, String keyword, Boolean enabled) {
        var query = query(keyword, enabled);
        return PageResult.of(mapper.selectPage(page.toPage(Map.of("name","name","code","code","sort","sort","createdAt","created_at")), query), this::view);
    }
    public List<OptionItem> options() {
        return mapper.selectList(query(null, true)).stream().map(row -> new OptionItem(row.getId(), row.getName(), false)).toList();
    }
    public PostEntity require(long id) {
        var row = mapper.selectById(id);
        if (row == null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        return row;
    }
    @Transactional
    public View save(Long id, Input input) {
        guard.lock();
        var row = id == null ? new PostEntity() : require(id);
        row.setName(input.name().trim()); row.setCode(input.code().trim()); row.setSort(input.sort());
        row.setEnabled(input.enabled()); row.setDescription(input.description());
        try { if (id == null) mapper.insert(row); else mapper.updateById(row); }
        catch (DuplicateKeyException ex) { throw new BizException(IamErrorCode.DUPLICATE_CODE); }
        return view(row);
    }
    @Transactional
    public void delete(long id) {
        guard.lock(); require(id);
        if (references.stream().anyMatch(ref -> ref.postUsed(id))) throw new BizException(IamErrorCode.REFERENCED);
        mapper.deleteById(id);
    }
    private LambdaQueryWrapper<PostEntity> query(String keyword, Boolean enabled) {
        var query = new LambdaQueryWrapper<PostEntity>().orderByAsc(PostEntity::getSort).orderByAsc(PostEntity::getId);
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like(PostEntity::getName, keyword).or().like(PostEntity::getCode, keyword));
        if (enabled != null) query.eq(PostEntity::getEnabled, enabled);
        return query;
    }
    private View view(PostEntity row) { return new View(row.getId(), row.getName(), row.getCode(), row.getSort(), row.getEnabled(), row.getDescription()); }
}
