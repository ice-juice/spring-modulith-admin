package com.sma.admin.file.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
@Component public class FilePermissionCatalog implements PermissionCatalog {
 public List<PermissionSpec> permissions(){return List.of(
  new PermissionSpec("file:center",null,"file.menu.center","文件中心","DIRECTORY",null,"Folder",50,"admin",true),
  new PermissionSpec("file:file:view","file:center","file.menu.files","文件管理","MENU","/file/files",null,0,"admin",true),
  new PermissionSpec("file:file:upload","file:file:view","file.action.upload","上传文件","BUTTON",null,null,0,"admin",true),
  new PermissionSpec("file:file:delete","file:file:view","file.action.delete","删除文件","BUTTON",null,null,0,"admin",true));}
}
