package com.sma.admin.workspace.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("workspace_note")
public class NoteEntity extends TenantEntity {
 private Long userId; private String portal; private String requestKey; private String title; private String kind;
 private String content; private String itemsJson; private String color; private Boolean pinned; private String status;
 @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
 private Instant trashedAt;
 @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
 private Long relatedMenuId;
 private Long version;
}
