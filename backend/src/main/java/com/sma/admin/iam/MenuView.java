package com.sma.admin.iam;
import java.util.List;

public record MenuView(Long id,Long parentId,String name,String path,String icon,String permission,String type,String openMode,List<MenuView> children) {
    public MenuView { children=List.copyOf(children); }
}
