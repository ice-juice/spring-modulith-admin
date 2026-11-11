package com.sma.admin.workspace.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("workspace_shelf")
public class ShelfEntity extends TenantEntity {
 private Long userId; private String portal; private String displayMode; private Long version;
}
