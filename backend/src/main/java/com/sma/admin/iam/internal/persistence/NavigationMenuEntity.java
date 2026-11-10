package com.sma.admin.iam.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("iam_navigation_menu")
public class NavigationMenuEntity extends TenantEntity { private Long navigationId; private Long permissionId; }
