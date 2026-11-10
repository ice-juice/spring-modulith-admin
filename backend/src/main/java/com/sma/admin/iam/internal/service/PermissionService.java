package com.sma.admin.iam.internal.service;

import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.*;
import com.sma.admin.iam.internal.persistence.*;

@Service
@Transactional(readOnly=true)
public class PermissionService {
    private final PermissionMapper mapper;
    private final I18nTextMapper translations;
    private final StructureGuard guard;
    private final MessageSource messages;
    private final ApplicationEventPublisher events;
    public PermissionService(PermissionMapper mapper,I18nTextMapper translations,StructureGuard guard,MessageSource messages,ApplicationEventPublisher events) {
        this.mapper=mapper; this.translations=translations; this.guard=guard; this.messages=messages; this.events=events;
    }
    public record Input(Long parentId, @NotBlank @Pattern(regexp="[a-z]+(:[a-z][a-z0-9-]*){1,3}") String permissionKey,
            @NotBlank @Size(max=128) String name, @Size(max=256) String nameKey,
            @NotBlank @Pattern(regexp="DIRECTORY|MENU|BUTTON|LOGIC") String type,
            @Size(max=1024) String path, @Size(max=64) String icon,
            @NotBlank @Pattern(regexp="ROUTE|EXTERNAL|IFRAME") String openMode,
            @NotNull @Min(0) Integer sort, @NotNull Boolean enabled, @NotNull Boolean hidden,
            @NotNull Boolean superAdminDefault, Map<String,String> translations) {}
    public record View(Long id,Long parentId,String portal,String permissionKey,String name,String defaultName,String nameKey,
            String type,String path,String icon,String openMode,int sort,boolean enabled,boolean hidden,boolean builtin,
            boolean superAdminDefault,Map<String,String> translations,List<View> children) {}

    public List<PermissionEntity> entities() {
        return mapper.selectList(inPortal().orderByAsc(PermissionEntity::getSort).orderByAsc(PermissionEntity::getId));
    }
    public List<View> list() {
        String locale = LocaleContextHolder.getLocale().toLanguageTag();
        var localized = translationMap();
        return entities().stream().map(row -> view(row,localized.getOrDefault(row.getId(),Map.of()),locale,List.of())).toList();
    }
    public List<View> tree() {
        var rows=list();
        Map<Long,List<View>> children=new HashMap<>();
        rows.forEach(row -> children.computeIfAbsent(row.parentId(), key -> new ArrayList<>()).add(row));
        return children.getOrDefault(0L,List.of()).stream().map(row -> branch(row,children)).toList();
    }
    public PermissionEntity require(long id) {
        var row=mapper.selectOne(inPortal().eq(PermissionEntity::getId,id));
        if (row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);
        return row;
    }
    @Transactional
    public View save(Long id,Input input) {
        guard.lock();
        var row=id==null?new PermissionEntity():require(id);
        if (Boolean.TRUE.equals(row.getBuiltin()) && (!row.getPermissionKey().equals(input.permissionKey()) || !row.getType().equals(input.type()))) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        long parentId=input.parentId()==null?0:input.parentId();
        Set<Long> seen=new HashSet<>();
        long cursor=parentId;
        while(cursor!=0) {
            if (Objects.equals(id,cursor)||!seen.add(cursor)) throw new BizException(IamErrorCode.INVALID_PARENT);
            var parent=require(cursor);
            if (!Set.of("DIRECTORY","MENU").contains(parent.getType())) throw new BizException(IamErrorCode.INVALID_PARENT);
            cursor=parent.getParentId();
        }
        if ("MENU".equals(input.type()) && (input.path()==null||input.path().isBlank())) throw new BizException(CommonErrorCode.BAD_REQUEST);
        if (input.path()!=null && !input.path().isBlank()) {
            boolean valid="ROUTE".equals(input.openMode())?input.path().startsWith("/")&&!input.path().startsWith("//"):
                    input.path().matches("https?://[^\\s]+");
            if (!valid) throw new BizException(CommonErrorCode.BAD_REQUEST);
        }
        Map<String,String> texts=input.translations()==null?Map.of():Map.copyOf(input.translations());
        if (!Set.of("zh-CN","en-US").containsAll(texts.keySet())||texts.values().stream().anyMatch(text -> text.isBlank()||text.length()>128)) throw new BizException(CommonErrorCode.BAD_REQUEST);
        row.setParentId(parentId); row.setPortal(CurrentUser.portal()); row.setPermissionKey(input.permissionKey());
        row.setName(input.name().trim()); row.setNameKey(input.nameKey()); row.setType(input.type()); row.setPath(input.path());
        row.setIcon(input.icon()); row.setOpenMode(input.openMode()); row.setSort(input.sort()); row.setEnabled(input.enabled());
        row.setHidden(input.hidden()); row.setSuperAdminDefault(input.superAdminDefault());
        if (id==null) row.setBuiltin(false);
        try { if (id==null) mapper.insert(row); else mapper.updateById(row); }
        catch (DuplicateKeyException ex) { throw new BizException(IamErrorCode.DUPLICATE_CODE); }
        translations.delete(translationQuery(row.getId()));
        texts.forEach((locale,text) -> {
            var translation=new I18nTextEntity(); translation.setEntityType("permission"); translation.setEntityId(row.getId());
            translation.setField("name"); translation.setLocale(locale); translation.setText(text); translations.insert(translation);
        });
        events.publishEvent(new PermissionChangedEvent(CurrentUser.tenantId(),CurrentUser.portal(),null,"resource"));
        return view(row,texts,LocaleContextHolder.getLocale().toLanguageTag(),List.of());
    }
    @Transactional
    public void delete(long id) {
        guard.lock(); var row=require(id);
        if (row.getBuiltin()) throw new BizException(CommonErrorCode.FORBIDDEN);
        if (mapper.selectCount(inPortal().eq(PermissionEntity::getParentId,id))>0) throw new BizException(IamErrorCode.HAS_CHILDREN);
        mapper.deleteById(id); translations.delete(translationQuery(id));
        events.publishEvent(new PermissionChangedEvent(CurrentUser.tenantId(),CurrentUser.portal(),null,"resource"));
    }
    /** Adds missing code-declared permissions, preserving administrator overrides. Requires parent-before-child order. */
    @Transactional
    public void sync(List<PermissionSpec> specifications) {
        guard.lock();
        Map<String,PermissionEntity> known=new HashMap<>();
        entities().forEach(row -> known.put(row.getPermissionKey(),row));
        for (var spec:specifications) {
            if (!CurrentUser.portal().equals(spec.portal())) continue;
            if (known.containsKey(spec.key())) continue;
            var row=new PermissionEntity();
            var parent=spec.parentKey()==null?null:known.get(spec.parentKey());
            if (spec.parentKey()!=null&&parent==null) throw new IllegalStateException("Unknown permission parent: "+spec.parentKey());
            row.setParentId(parent==null?0L:parent.getId()); row.setPortal(spec.portal()); row.setPermissionKey(spec.key());
            row.setName(spec.defaultName()); row.setNameKey(spec.nameKey()); row.setType(spec.type()); row.setPath(spec.path());
            row.setIcon(spec.icon()); row.setSort(spec.sort()); row.setOpenMode("ROUTE"); row.setEnabled(true); row.setHidden(false);
            row.setBuiltin(true); row.setSuperAdminDefault(spec.superAdminDefault()); mapper.insert(row); known.put(spec.key(),row);
        }
    }
    private View branch(View row,Map<Long,List<View>> children) {
        return new View(row.id(),row.parentId(),row.portal(),row.permissionKey(),row.name(),row.defaultName(),row.nameKey(),row.type(),row.path(),row.icon(),
                row.openMode(),row.sort(),row.enabled(),row.hidden(),row.builtin(),row.superAdminDefault(),row.translations(),
                children.getOrDefault(row.id(),List.of()).stream().map(child -> branch(child,children)).toList());
    }
    private Map<Long,Map<String,String>> translationMap() {
        Map<Long,Map<String,String>> result=new HashMap<>();
        translations.selectList(new LambdaQueryWrapper<I18nTextEntity>().eq(I18nTextEntity::getEntityType,"permission").eq(I18nTextEntity::getField,"name"))
                .forEach(row -> result.computeIfAbsent(row.getEntityId(), key -> new HashMap<>()).put(row.getLocale(),row.getText()));
        return result;
    }
    private View view(PermissionEntity row,Map<String,String> texts,String locale,List<View> children) {
        String name=texts.get(locale);
        if (name==null && row.getNameKey()!=null && !row.getNameKey().isBlank()) name=messages.getMessage(row.getNameKey(),null,row.getName(),Locale.forLanguageTag(locale));
        if (name==null) name=row.getName();
        return new View(row.getId(),row.getParentId(),row.getPortal(),row.getPermissionKey(),name,row.getName(),row.getNameKey(),row.getType(),
                row.getPath(),row.getIcon(),row.getOpenMode(),row.getSort(),row.getEnabled(),row.getHidden(),row.getBuiltin(),row.getSuperAdminDefault(),texts,children);
    }
    private LambdaQueryWrapper<PermissionEntity> inPortal() { return new LambdaQueryWrapper<PermissionEntity>().eq(PermissionEntity::getPortal,CurrentUser.portal()); }
    private LambdaQueryWrapper<I18nTextEntity> translationQuery(long id) {
        return new LambdaQueryWrapper<I18nTextEntity>().eq(I18nTextEntity::getEntityType,"permission").eq(I18nTextEntity::getEntityId,id).eq(I18nTextEntity::getField,"name");
    }
}
