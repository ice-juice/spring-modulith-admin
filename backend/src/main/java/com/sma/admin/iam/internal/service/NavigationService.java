package com.sma.admin.iam.internal.service;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.*;
import com.sma.admin.iam.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class NavigationService implements NavigationQuery {
    private final NavigationMapper mapper;
    private final NavigationMenuMapper menus;
    private final PermissionService permissions;
    private final I18nTextMapper texts;
    private final StructureGuard guard;
    public NavigationService(NavigationMapper mapper,NavigationMenuMapper menus,PermissionService permissions,I18nTextMapper texts,StructureGuard guard) {
        this.mapper=mapper; this.menus=menus; this.permissions=permissions; this.texts=texts; this.guard=guard;
    }
    public record Input(@NotBlank @Size(max=128) String name,@Size(max=64) String icon,@NotNull @Min(0) Integer sort,@NotNull Boolean enabled,
            @NotNull @Size(max=200) Set<@NotNull Long> menuIds,Map<String,String> translations) {}
    public record View(Long id,String name,String defaultName,String icon,int sort,boolean enabled,Set<Long> menuIds,Map<String,String> translations) {}
    public List<View> list() {
        var rows=mapper.selectList(new LambdaQueryWrapper<NavigationEntity>().eq(NavigationEntity::getPortal,CurrentUser.portal())
                .orderByAsc(NavigationEntity::getSort).orderByAsc(NavigationEntity::getId));
        return rows.stream().map(this::view).toList();
    }
    private NavigationEntity require(long id) {
        var row=mapper.selectOne(new LambdaQueryWrapper<NavigationEntity>().eq(NavigationEntity::getId,id).eq(NavigationEntity::getPortal,CurrentUser.portal()));
        if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND); return row;
    }
    @Transactional
    public View save(Long id,Input input) {
        guard.lock(); var row=id==null?new NavigationEntity():require(id);
        for(Long menuId:input.menuIds()) {
            var menu=permissions.require(menuId);
            if(menu.getParentId()!=0||!Set.of("DIRECTORY","MENU").contains(menu.getType())) throw new BizException(CommonErrorCode.BAD_REQUEST);
        }
        Map<String,String> translations=input.translations()==null?Map.of():Map.copyOf(input.translations());
        if(!Set.of("zh-CN","en-US").containsAll(translations.keySet())||translations.values().stream().anyMatch(text -> text.isBlank()||text.length()>128)) throw new BizException(CommonErrorCode.BAD_REQUEST);
        row.setPortal(CurrentUser.portal()); row.setName(input.name().trim()); row.setIcon(input.icon()); row.setSort(input.sort()); row.setEnabled(input.enabled());
        if(id==null) mapper.insert(row); else mapper.updateById(row);
        menus.delete(new LambdaQueryWrapper<NavigationMenuEntity>().eq(NavigationMenuEntity::getNavigationId,row.getId()));
        for(Long menuId:input.menuIds()) { var relation=new NavigationMenuEntity(); relation.setNavigationId(row.getId()); relation.setPermissionId(menuId); menus.insert(relation); }
        texts.delete(textQuery(row.getId()));
        translations.forEach((locale,text) -> {
            var translation=new I18nTextEntity(); translation.setEntityType("navigation"); translation.setEntityId(row.getId());
            translation.setField("name"); translation.setLocale(locale); translation.setText(text); texts.insert(translation);
        });
        return view(row);
    }
    @Transactional public void delete(long id) {
        guard.lock(); require(id); mapper.deleteById(id);
        menus.delete(new LambdaQueryWrapper<NavigationMenuEntity>().eq(NavigationMenuEntity::getNavigationId,id)); texts.delete(textQuery(id));
    }
    @Override public List<NavigationView> visible(Set<Long> menuIds) {
        return list().stream().filter(View::enabled).map(row -> new NavigationView(row.id(),row.name(),row.icon(),
                row.menuIds().stream().filter(menuIds::contains).collect(java.util.stream.Collectors.toSet()))).filter(row -> !row.menuIds().isEmpty()).toList();
    }
    private View view(NavigationEntity row) {
        Set<Long> menuIds=menus.selectList(new LambdaQueryWrapper<NavigationMenuEntity>().eq(NavigationMenuEntity::getNavigationId,row.getId()))
                .stream().map(NavigationMenuEntity::getPermissionId).collect(java.util.stream.Collectors.toSet());
        Map<String,String> translations=new HashMap<>();
        texts.selectList(textQuery(row.getId())).forEach(text -> translations.put(text.getLocale(),text.getText()));
        return new View(row.getId(),translations.getOrDefault(LocaleContextHolder.getLocale().toLanguageTag(),row.getName()),row.getName(),row.getIcon(),row.getSort(),row.getEnabled(),menuIds,translations);
    }
    private LambdaQueryWrapper<I18nTextEntity> textQuery(long id) {
        return new LambdaQueryWrapper<I18nTextEntity>().eq(I18nTextEntity::getEntityType,"navigation").eq(I18nTextEntity::getEntityId,id).eq(I18nTextEntity::getField,"name");
    }
}
