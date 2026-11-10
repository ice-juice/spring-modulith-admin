package com.sma.admin.config.internal.service;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class DictionaryService implements DictionaryQuery {
    private final DictionaryTypeMapper types;
    private final DictionaryItemMapper items;
    private final ConfigNames names;
    private final WriteLockMapper lock;
    private final ApplicationEventPublisher events;
    public DictionaryService(DictionaryTypeMapper types,DictionaryItemMapper items,ConfigNames names,WriteLockMapper lock,ApplicationEventPublisher events) {
        this.types=types; this.items=items; this.names=names; this.lock=lock; this.events=events;
    }
    public record TypeInput(@NotBlank @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String typeKey,@NotBlank @Size(max=128) String name,
            @Size(max=256) String nameKey,@NotNull Boolean tree,@NotNull Boolean enabled,@NotNull @Min(0) Integer sort,Map<String,String> translations) {}
    public record TypeView(Long id,String typeKey,String name,String defaultName,String nameKey,boolean tree,boolean enabled,int sort,boolean builtin,Map<String,String> translations) {}
    public record ItemInput(@NotNull Long typeId,Long parentId,@NotBlank @Size(max=128) String value,@NotBlank @Size(max=128) String name,
            @Size(max=256) String nameKey,@NotNull Boolean enabled,@NotNull @Min(0) Integer sort,Map<String,String> translations) {}
    public record ItemView(Long id,Long typeId,Long parentId,String value,String name,String defaultName,String nameKey,boolean enabled,int sort,boolean builtin,Map<String,String> translations) {}
    public List<TypeView> types() { return types.selectList(new LambdaQueryWrapper<DictionaryTypeEntity>().orderByAsc(DictionaryTypeEntity::getSort).orderByAsc(DictionaryTypeEntity::getId)).stream().map(this::typeView).toList(); }
    public List<ItemView> items(long typeId) {
        requireType(typeId);
        return items.selectList(new LambdaQueryWrapper<DictionaryItemEntity>().eq(DictionaryItemEntity::getTypeId,typeId).orderByAsc(DictionaryItemEntity::getSort).orderByAsc(DictionaryItemEntity::getId)).stream().map(this::itemView).toList();
    }
    private DictionaryTypeEntity requireType(long id) { var row=types.selectById(id); if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND); return row; }
    private DictionaryItemEntity requireItem(long id) { var row=items.selectById(id); if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND); return row; }
    @Transactional public TypeView saveType(Long id,TypeInput input) {
        lock.lock(CurrentUser.tenantId()); var row=id==null?new DictionaryTypeEntity():requireType(id);
        if(Boolean.TRUE.equals(row.getBuiltin())&&(!row.getTypeKey().equals(input.typeKey())||!input.enabled()||!row.getTree().equals(input.tree()))) throw new BizException(ConfigErrorCode.BUILTIN);
        if(id!=null&&!input.tree()&&items.selectCount(new LambdaQueryWrapper<DictionaryItemEntity>().eq(DictionaryItemEntity::getTypeId,id).ne(DictionaryItemEntity::getParentId,0))>0) throw new BizException(ConfigErrorCode.HAS_CHILDREN);
        row.setTypeKey(input.typeKey()); row.setName(input.name().trim()); row.setNameKey(input.nameKey()); row.setTree(input.tree()); row.setEnabled(input.enabled()); row.setSort(input.sort());
        if(id==null) row.setBuiltin(false);
        try { if(id==null) types.insert(row); else types.updateById(row); } catch(DuplicateKeyException ex) { throw new BizException(ConfigErrorCode.DUPLICATE); }
        names.replace("dictionary-type",row.getId(),input.translations()); changed(row.getTypeKey()); return typeView(row);
    }
    @Transactional public ItemView saveItem(Long id,ItemInput input) {
        lock.lock(CurrentUser.tenantId()); var type=requireType(input.typeId()); var row=id==null?new DictionaryItemEntity():requireItem(id);
        if(id!=null&&!row.getTypeId().equals(type.getId())) throw new BizException(CommonErrorCode.BAD_REQUEST);
        if(Boolean.TRUE.equals(row.getBuiltin())&&(!row.getItemValue().equals(input.value())||!input.enabled())) throw new BizException(ConfigErrorCode.BUILTIN);
        long parentId=input.parentId()==null?0:input.parentId();
        if(Boolean.TRUE.equals(row.getBuiltin())&&!row.getParentId().equals(parentId)) throw new BizException(ConfigErrorCode.BUILTIN);
        if(!type.getTree()&&parentId!=0) throw new BizException(ConfigErrorCode.INVALID_PARENT);
        long cursor=parentId; Set<Long> visited=new HashSet<>();
        while(cursor!=0) {
            if(Objects.equals(id,cursor)||!visited.add(cursor)) throw new BizException(ConfigErrorCode.INVALID_PARENT);
            var parent=requireItem(cursor);
            if(!parent.getTypeId().equals(type.getId())) throw new BizException(ConfigErrorCode.INVALID_PARENT);
            cursor=parent.getParentId();
        }
        row.setTypeId(type.getId()); row.setParentId(parentId); row.setName(input.name().trim()); row.setNameKey(input.nameKey()); row.setItemValue(input.value()); row.setEnabled(input.enabled()); row.setSort(input.sort());
        if(id==null) row.setBuiltin(false);
        try { if(id==null) items.insert(row); else items.updateById(row); } catch(DuplicateKeyException ex) { throw new BizException(ConfigErrorCode.DUPLICATE); }
        names.replace("dictionary-item",row.getId(),input.translations()); changed(type.getTypeKey()); return itemView(row);
    }
    @Transactional public void deleteType(long id) {
        lock.lock(CurrentUser.tenantId()); var row=requireType(id);
        if(row.getBuiltin()) throw new BizException(ConfigErrorCode.BUILTIN);
        if(items.selectCount(new LambdaQueryWrapper<DictionaryItemEntity>().eq(DictionaryItemEntity::getTypeId,id))>0) throw new BizException(ConfigErrorCode.HAS_CHILDREN);
        types.deleteById(id); names.replace("dictionary-type",id,Map.of()); changed(row.getTypeKey());
    }
    @Transactional public void deleteItem(long id) {
        lock.lock(CurrentUser.tenantId()); var row=requireItem(id);
        if(row.getBuiltin()) throw new BizException(ConfigErrorCode.BUILTIN);
        if(items.selectCount(new LambdaQueryWrapper<DictionaryItemEntity>().eq(DictionaryItemEntity::getParentId,id))>0) throw new BizException(ConfigErrorCode.HAS_CHILDREN);
        items.deleteById(id); names.replace("dictionary-item",id,Map.of()); changed(requireType(row.getTypeId()).getTypeKey());
    }
    @Override public List<DictionaryOption> options(String typeKey) {
        var type=types.selectOne(new LambdaQueryWrapper<DictionaryTypeEntity>().eq(DictionaryTypeEntity::getTypeKey,typeKey).eq(DictionaryTypeEntity::getEnabled,true));
        if(type==null) return List.of();
        Map<Long,List<ItemView>> children=new LinkedHashMap<>();
        items(type.getId()).stream().filter(ItemView::enabled).forEach(row -> children.computeIfAbsent(row.parentId(),key -> new ArrayList<>()).add(row));
        return children.getOrDefault(0L,List.of()).stream().map(row -> option(row,children)).toList();
    }
    private DictionaryOption option(ItemView row,Map<Long,List<ItemView>> children) { return new DictionaryOption(row.value(),row.name(),children.getOrDefault(row.id(),List.of()).stream().map(child -> option(child,children)).toList()); }
    private TypeView typeView(DictionaryTypeEntity row) { var texts=names.translations("dictionary-type",row.getId()); return new TypeView(row.getId(),row.getTypeKey(),names.name(row.getName(),row.getNameKey(),texts),row.getName(),row.getNameKey(),row.getTree(),row.getEnabled(),row.getSort(),row.getBuiltin(),texts); }
    private ItemView itemView(DictionaryItemEntity row) { var texts=names.translations("dictionary-item",row.getId()); return new ItemView(row.getId(),row.getTypeId(),row.getParentId(),row.getItemValue(),names.name(row.getName(),row.getNameKey(),texts),row.getName(),row.getNameKey(),row.getEnabled(),row.getSort(),row.getBuiltin(),texts); }
    private void changed(String key) { events.publishEvent(new ConfigurationChangedEvent(CurrentUser.tenantId(),"dictionary",key)); }

    public record SeedItem(String value,String name,String nameKey,Integer parentIndex) {}
    public record SeedType(String key,String name,String nameKey,boolean tree,List<SeedItem> items) {}
    @Transactional public void initialize(List<SeedType> seeds) {
        lock.lock(CurrentUser.tenantId());
        for(var seed:seeds) {
            var type=types.selectOne(new LambdaQueryWrapper<DictionaryTypeEntity>().eq(DictionaryTypeEntity::getTypeKey,seed.key()));
            if(type==null) {
                var view=saveType(null,new TypeInput(seed.key(),seed.name(),seed.nameKey(),seed.tree(),true,0,Map.of())); type=requireType(view.id()); type.setBuiltin(true); types.updateById(type);
            }
            List<Long> ids=new ArrayList<>();
            for(int i=0;i<seed.items().size();i++) {
                var spec=seed.items().get(i);
                var item=items.selectOne(new LambdaQueryWrapper<DictionaryItemEntity>().eq(DictionaryItemEntity::getTypeId,type.getId()).eq(DictionaryItemEntity::getItemValue,spec.value()));
                if(item==null) {
                    var view=saveItem(null,new ItemInput(type.getId(),spec.parentIndex()==null?0L:ids.get(spec.parentIndex()),spec.value(),spec.name(),spec.nameKey(),true,i,Map.of()));
                    item=requireItem(view.id()); item.setBuiltin(true); items.updateById(item);
                }
                ids.add(item.getId());
            }
        }
    }
}
