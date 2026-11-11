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
    private final List<OrganizationReference> references;
    private final RolePermissionMapper rolePermissions;
    private final NavigationMenuMapper navigationMenus;
    private final IamDisplayCaches cache;
    public PermissionService(PermissionMapper mapper,I18nTextMapper translations,StructureGuard guard,MessageSource messages,ApplicationEventPublisher events,List<OrganizationReference> references,
            RolePermissionMapper rolePermissions,NavigationMenuMapper navigationMenus,IamDisplayCaches cache) {
        this.mapper=mapper; this.translations=translations; this.guard=guard; this.messages=messages; this.events=events;
        this.references=references;
        this.rolePermissions=rolePermissions; this.navigationMenus=navigationMenus;
        this.cache=cache;
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
            boolean superAdminDefault,Map<String,String> translations,List<View> children) {
        public View { translations=Map.copyOf(translations); children=List.copyOf(children); }
    }

    public List<PermissionEntity> entities() {
        return mapper.selectList(inPortal().orderByAsc(PermissionEntity::getSort).orderByAsc(PermissionEntity::getId));
    }
    public record DisplaySnapshot(List<View> rows) { public DisplaySnapshot { rows=List.copyOf(rows); } }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.SUPPORTS,readOnly=true)
    public List<View> list() {
        return cache.get(IamDisplayCaches.PERMISSIONS,LocaleContextHolder.getLocale().toLanguageTag(),DisplaySnapshot.class,
                ()->new DisplaySnapshot(loadList())).rows();
    }
    private List<View> loadList() {
        String locale = LocaleContextHolder.getLocale().toLanguageTag();
        var localized = translationMap();
        return entities().stream().map(row -> view(row,localized.getOrDefault(row.getId(),Map.of()),locale,List.of())).toList();
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.SUPPORTS,readOnly=true)
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
        cache.invalidate(IamDisplayCaches.PERMISSIONS);
        events.publishEvent(new PermissionChangedEvent(CurrentUser.tenantId(),CurrentUser.portal(),null,"resource"));
        return view(row,texts,LocaleContextHolder.getLocale().toLanguageTag(),List.of());
    }
    @Transactional
    public void delete(long id) {
        guard.lock(); var row=require(id);
        if (row.getBuiltin()) throw new BizException(CommonErrorCode.FORBIDDEN);
        if (references.stream().anyMatch(ref -> ref.permissionUsed(id))) throw new BizException(IamErrorCode.REFERENCED);
        if (mapper.selectCount(inPortal().eq(PermissionEntity::getParentId,id))>0) throw new BizException(IamErrorCode.HAS_CHILDREN);
        mapper.deleteById(id); translations.delete(translationQuery(id));
        cache.invalidate(IamDisplayCaches.PERMISSIONS);
        events.publishEvent(new PermissionChangedEvent(CurrentUser.tenantId(),CurrentUser.portal(),null,"resource"));
    }
    /** Adds declarations and upgrades untouched auto-discovered logic labels, preserving administrator overrides. */
    @Transactional
    public void sync(List<PermissionSpec> specifications) {
        guard.lock();
        Map<String,PermissionEntity> known=new HashMap<>();
        entities().forEach(row -> known.put(row.getPermissionKey(),row));
        for (var spec:specifications) {
            if (!CurrentUser.portal().equals(spec.portal())) continue;
            var existing=known.get(spec.key());
            if (existing!=null) {
                // Root catalog resources can move into a directory, including old pathless buttons
                // whose page has become available. Preserve custom parents and every leaf grant.
                boolean menuPromotion = "BUTTON".equals(existing.getType()) && "MENU".equals(spec.type())
                        && (existing.getPath()==null || existing.getPath().isBlank()) && spec.path()!=null
                        && spec.path().startsWith("/") && !spec.path().startsWith("//");
                boolean unchangedResource = Objects.equals(existing.getType(),spec.type())
                        && Set.of("MENU","BUTTON").contains(existing.getType()) && Objects.equals(existing.getPath(),spec.path());
                if (Boolean.TRUE.equals(existing.getBuiltin()) && (menuPromotion || unchangedResource)
                        && Objects.equals(existing.getParentId(),0L)
                        && spec.parentKey()!=null && spec.nameKey()!=null
                        && Objects.equals(existing.getNameKey(),spec.nameKey())
                        && "ROUTE".equals(existing.getOpenMode())) {
                    var parent=known.get(spec.parentKey());
                    if(parent==null) throw new IllegalStateException("Unknown permission parent: "+spec.parentKey());
                    if ("DIRECTORY".equals(parent.getType()) && Objects.equals(parent.getParentId(),0L)) {
                        if (menuPromotion) {
                            existing.setType("MENU"); existing.setPath(spec.path());
                            if (existing.getIcon()==null || existing.getIcon().isBlank()) existing.setIcon(spec.icon());
                        }
                        moveRootMenu(existing,parent);
                    }
                }
                // Promote a previously cataloged pathless action to its now available root menu.
                // Only the builtin's catalog-owned route/type change; customized display, flags and grants survive.
                if (Boolean.TRUE.equals(existing.getBuiltin()) && "BUTTON".equals(existing.getType())
                        && "MENU".equals(spec.type()) && spec.path()!=null && spec.path().startsWith("/") && !spec.path().startsWith("//")
                        && spec.parentKey()==null && Objects.equals(existing.getParentId(),0L)
                        && (existing.getPath()==null || existing.getPath().isBlank()) && "ROUTE".equals(existing.getOpenMode())
                        && spec.nameKey()!=null && Objects.equals(existing.getNameKey(),spec.nameKey())) {
                    existing.setType("MENU"); existing.setPath(spec.path());
                    if (existing.getIcon()==null || existing.getIcon().isBlank()) existing.setIcon(spec.icon());
                    mapper.updateById(existing);
                }
                // Older versions auto-discovered these actions at the root with the key as their name.
                // Keep IDs/grants, translations, switches and sorting. Never overwrite a customized label or parent.
                if (Boolean.TRUE.equals(existing.getBuiltin()) && "LOGIC".equals(existing.getType())
                        && Objects.equals(existing.getName(),spec.key())
                        && (existing.getNameKey()==null||existing.getNameKey().isBlank())
                        && Objects.equals(existing.getParentId(),0L) && spec.parentKey()!=null && spec.nameKey()!=null) {
                    var parent=known.get(spec.parentKey());
                    if(parent==null) throw new IllegalStateException("Unknown permission parent: "+spec.parentKey());
                    existing.setName(spec.defaultName()); existing.setNameKey(spec.nameKey());
                    existing.setParentId(parent.getId()); existing.setType(spec.type());
                    mapper.updateById(existing);
                }
                continue;
            }
            var row=new PermissionEntity();
            var parent=spec.parentKey()==null?null:known.get(spec.parentKey());
            if (spec.parentKey()!=null&&parent==null) throw new IllegalStateException("Unknown permission parent: "+spec.parentKey());
            row.setParentId(parent==null?0L:parent.getId()); row.setPortal(spec.portal()); row.setPermissionKey(spec.key());
            row.setName(spec.defaultName()); row.setNameKey(spec.nameKey()); row.setType(spec.type()); row.setPath(spec.path());
            row.setIcon(spec.icon()); row.setSort(spec.sort()); row.setOpenMode("ROUTE"); row.setEnabled(true); row.setHidden(false);
            row.setBuiltin(true); row.setSuperAdminDefault(spec.superAdminDefault()); mapper.insert(row); known.put(spec.key(),row);
        }
        cache.invalidate(IamDisplayCaches.PERMISSIONS);
        cache.invalidate(IamDisplayCaches.NAVIGATION);
    }
    private void moveRootMenu(PermissionEntity menu,PermissionEntity parent) {
        menu.setParentId(parent.getId()); mapper.updateById(menu);
        // Existing role grants need the new navigation ancestor, never a new leaf privilege.
        var grants=rolePermissions.selectList(new LambdaQueryWrapper<RolePermissionEntity>()
                .eq(RolePermissionEntity::getPermissionId,menu.getId()));
        for(var grant:grants) {
            var query=new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId,grant.getRoleId())
                    .eq(RolePermissionEntity::getPermissionId,parent.getId());
            if(rolePermissions.selectCount(query)==0) {
                var ancestor=new RolePermissionEntity(); ancestor.setRoleId(grant.getRoleId());
                ancestor.setPermissionId(parent.getId()); rolePermissions.insert(ancestor);
            }
        }
        // Navigation groups only store root entries. Keep the group linked after the move.
        var links=navigationMenus.selectList(new LambdaQueryWrapper<NavigationMenuEntity>()
                .eq(NavigationMenuEntity::getPermissionId,menu.getId()));
        for(var link:links) {
            var query=new LambdaQueryWrapper<NavigationMenuEntity>().eq(NavigationMenuEntity::getNavigationId,link.getNavigationId())
                    .eq(NavigationMenuEntity::getPermissionId,parent.getId());
            if(navigationMenus.selectCount(query)==0) {
                link.setPermissionId(parent.getId()); navigationMenus.updateById(link);
            } else navigationMenus.deleteById(link.getId());
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
