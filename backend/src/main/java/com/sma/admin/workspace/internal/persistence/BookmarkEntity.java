package com.sma.admin.workspace.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("workspace_bookmark")
public class BookmarkEntity extends TenantEntity {
 private Long userId; private String portal; private Long menuId; private String alias; private Integer position;
}
