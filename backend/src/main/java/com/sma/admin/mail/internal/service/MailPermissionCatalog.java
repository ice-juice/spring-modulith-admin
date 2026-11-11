package com.sma.admin.mail.internal.service;

import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.PermissionCatalog;
import com.sma.admin.common.security.PermissionSpec;

/** Logical capabilities until the C8 management UI is delivered; no navigation to a missing page. */
@Component
public class MailPermissionCatalog implements PermissionCatalog {
    @Override public List<PermissionSpec> permissions() {
        return List.of(
                new PermissionSpec("mail:profile:view", null, "mail.permission.profile-view", "查看邮件配置", "LOGIC", null, null, 0, "admin", true),
                new PermissionSpec("mail:profile:manage", null, "mail.permission.profile-manage", "管理邮件配置", "LOGIC", null, null, 1, "admin", true),
                new PermissionSpec("mail:template:view", null, "mail.permission.template-view", "查看邮件模板", "LOGIC", null, null, 2, "admin", true),
                new PermissionSpec("mail:template:manage", null, "mail.permission.template-manage", "编辑邮件模板", "LOGIC", null, null, 3, "admin", true),
                new PermissionSpec("mail:template:publish", null, "mail.permission.template-publish", "发布或停用邮件模板", "LOGIC", null, null, 4, "admin", true),
                new PermissionSpec("mail:delivery:view", null, "mail.permission.delivery-view", "查看邮件任务", "LOGIC", null, null, 5, "admin", true),
                new PermissionSpec("mail:delivery:manage", null, "mail.permission.delivery-manage", "管理邮件任务", "LOGIC", null, null, 6, "admin", true));
    }
}
