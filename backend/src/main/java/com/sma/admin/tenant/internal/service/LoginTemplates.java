package com.sma.admin.tenant.internal.service;

import java.util.List;
import org.springframework.stereotype.Component;

/** IDs select locally compiled front-end components; never a caller-controlled class, URL or import expression. */
@Component
public class LoginTemplates {
    public record Template(String id,int version,String nameKey,String layout,boolean enabled) {}
    private static final List<Template> CATALOG=List.of(
            new Template("login1",1,"tenant.template.login1","SPLIT",true),
            new Template("login2",1,"tenant.template.login2","CENTERED",true),
            new Template("login3",1,"tenant.template.login3","BACKGROUND",true));
    public List<Template> all() { return CATALOG; }
    public boolean available(String id) { return CATALOG.stream().anyMatch(template -> template.enabled() && template.id().equals(id)); }
    public String effective(String id) { return available(id)?id:"login1"; }
}
