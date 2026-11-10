package com.sma.admin.message.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component
public class MessagePermissionCatalog implements PermissionCatalog {
 public List<PermissionSpec> permissions(){return List.of(
  new PermissionSpec("message:center",null,"message.menu.center","消息协同","DIRECTORY",null,"Bell",60,"admin",true),
  new PermissionSpec("message:notice:view","message:center","message.menu.notices","系统通知","MENU","/message/notices",null,0,"admin",true),
  new PermissionSpec("message:notice:create","message:notice:view","message.action.create","发布通知","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("message:notice:update","message:notice:view","message.action.update","编辑通知","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("message:notice:delete","message:notice:view","message.action.delete","回收通知","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("message:notice:restore","message:notice:view","message.action.restore","恢复通知","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("message:todo:view","message:center","message.menu.todos","待办管理","MENU","/message/todos",null,10,"admin",true),
  new PermissionSpec("message:todo:assign","message:todo:view","message.action.assign","分配待办","BUTTON",null,null,0,"admin",true));}
}
