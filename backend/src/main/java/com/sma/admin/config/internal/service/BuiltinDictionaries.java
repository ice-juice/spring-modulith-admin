package com.sma.admin.config.internal.service;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@Component
public class BuiltinDictionaries {
 private final DictionaryService service;
 public BuiltinDictionaries(DictionaryService service) { this.service=service; }
 @EventListener(ApplicationReadyEvent.class)
 public void initialize() { service.initialize(seeds()); }
 public static List<DictionaryService.SeedType> seeds() {
  return List.of(
   new DictionaryService.SeedType("options_boolean","选项 Boolean值","config.dict.options_boolean.name",false,List.of(
    new DictionaryService.SeedItem("true","是","config.dict.options_boolean.i0",null),
    new DictionaryService.SeedItem("false","否","config.dict.options_boolean.i1",null)
   )),
   new DictionaryService.SeedType("options_enable","启停选项","config.dict.options_enable.name",false,List.of(
    new DictionaryService.SeedItem("true","启用","config.dict.options_enable.i0",null),
    new DictionaryService.SeedItem("false","禁用","config.dict.options_enable.i1",null)
   )),
   new DictionaryService.SeedType("sex","性别","config.dict.sex.name",false,List.of(
    new DictionaryService.SeedItem("1","男","config.dict.sex.i0",null),
    new DictionaryService.SeedItem("2","女","config.dict.sex.i1",null)
   )),
   new DictionaryService.SeedType("dept_type","机构类型","config.dict.dept_type.name",false,List.of(
    new DictionaryService.SeedItem("1","总部","config.dict.dept_type.i0",null),
    new DictionaryService.SeedItem("2","省级公司","config.dict.dept_type.i1",null),
    new DictionaryService.SeedItem("3","市级公司","config.dict.dept_type.i2",null),
    new DictionaryService.SeedItem("4","区县级公司","config.dict.dept_type.i3",null),
    new DictionaryService.SeedItem("5","办事处","config.dict.dept_type.i4",null),
    new DictionaryService.SeedItem("6","部门","config.dict.dept_type.i5",null)
   )),
   new DictionaryService.SeedType("post_type","岗位类型","config.dict.post_type.name",false,List.of(
    new DictionaryService.SeedItem("1","高管","config.dict.post_type.i0",null),
    new DictionaryService.SeedItem("2","中层","config.dict.post_type.i1",null),
    new DictionaryService.SeedItem("3","基层","config.dict.post_type.i2",null),
    new DictionaryService.SeedItem("4","其他","config.dict.post_type.i3",null)
   )),
   new DictionaryService.SeedType("sys_notice_type","系统通知类型","config.dict.sys_notice_type.name",false,List.of(
    new DictionaryService.SeedItem("1","公告","config.dict.sys_notice_type.i0",null),
    new DictionaryService.SeedItem("2","新闻","config.dict.sys_notice_type.i1",null),
    new DictionaryService.SeedItem("3","会议","config.dict.sys_notice_type.i2",null),
    new DictionaryService.SeedItem("4","放假","config.dict.sys_notice_type.i3",null),
    new DictionaryService.SeedItem("5","其它","config.dict.sys_notice_type.i4",null)
   )),
   new DictionaryService.SeedType("sys_notice_priority_level","系统通知优先级","config.dict.sys_notice_priority_level.name",false,List.of(
    new DictionaryService.SeedItem("1","普通","config.dict.sys_notice_priority_level.i0",null),
    new DictionaryService.SeedItem("2","一般","config.dict.sys_notice_priority_level.i1",null),
    new DictionaryService.SeedItem("3","紧急","config.dict.sys_notice_priority_level.i2",null)
   )),
   new DictionaryService.SeedType("sys_notice_receiver_type","系统通知接收范围","config.dict.sys_notice_receiver_type.name",false,List.of(
    new DictionaryService.SeedItem("1","全部","config.dict.sys_notice_receiver_type.i0",null),
    new DictionaryService.SeedItem("2","角色","config.dict.sys_notice_receiver_type.i1",null),
    new DictionaryService.SeedItem("3","部门","config.dict.sys_notice_receiver_type.i2",null),
    new DictionaryService.SeedItem("4","岗位","config.dict.sys_notice_receiver_type.i3",null),
    new DictionaryService.SeedItem("5","用户","config.dict.sys_notice_receiver_type.i4",null)
   )),
   new DictionaryService.SeedType("todo_priority_level","待办优先级","config.dict.todo_priority_level.name",false,List.of(
    new DictionaryService.SeedItem("1","普通","config.dict.todo_priority_level.i0",null),
    new DictionaryService.SeedItem("2","一般","config.dict.todo_priority_level.i1",null),
    new DictionaryService.SeedItem("3","紧急","config.dict.todo_priority_level.i2",null)
   )),
   new DictionaryService.SeedType("todo_state","待办状态","config.dict.todo_state.name",false,List.of(
    new DictionaryService.SeedItem("1","未开始","config.dict.todo_state.i0",null),
    new DictionaryService.SeedItem("2","进行中","config.dict.todo_state.i1",null),
    new DictionaryService.SeedItem("3","已完成","config.dict.todo_state.i2",null),
    new DictionaryService.SeedItem("4","已取消","config.dict.todo_state.i3",null),
    new DictionaryService.SeedItem("5","未完成","config.dict.todo_state.i4",null)
   )),
   new DictionaryService.SeedType("todo_type","待办类型","config.dict.todo_type.name",false,List.of(
    new DictionaryService.SeedItem("1","无链接无内容","config.dict.todo_type.i0",null),
    new DictionaryService.SeedItem("2","无链接有内容","config.dict.todo_type.i1",null),
    new DictionaryService.SeedItem("3","有链接无内容","config.dict.todo_type.i2",null),
    new DictionaryService.SeedItem("4","有链接有内容","config.dict.todo_type.i3",null)
   )),
   new DictionaryService.SeedType("todo_datetime_column","待办时间查询列","config.dict.todo_datetime_column.name",false,List.of(
    new DictionaryService.SeedItem("1","创建时间","config.dict.todo_datetime_column.i0",null),
    new DictionaryService.SeedItem("2","更新时间","config.dict.todo_datetime_column.i1",null),
    new DictionaryService.SeedItem("3","待完成时间","config.dict.todo_datetime_column.i2",null),
    new DictionaryService.SeedItem("4","实际完成时间","config.dict.todo_datetime_column.i3",null),
    new DictionaryService.SeedItem("5","取消时间","config.dict.todo_datetime_column.i4",null)
   )),
   new DictionaryService.SeedType("extend_dic_demo","多级字典示例","config.dict.extend_dic_demo.name",true,List.of(
    new DictionaryService.SeedItem("v1","字典项1","config.dict.extend_dic_demo.i0",null),
    new DictionaryService.SeedItem("v2","字典项2","config.dict.extend_dic_demo.i1",null),
    new DictionaryService.SeedItem("v21","字典项2-1","config.dict.extend_dic_demo.i2",1),
    new DictionaryService.SeedItem("v22","字典项2-2","config.dict.extend_dic_demo.i3",1),
    new DictionaryService.SeedItem("v221","字典项2-2-1","config.dict.extend_dic_demo.i4",3),
    new DictionaryService.SeedItem("v23","字典项2-3","config.dict.extend_dic_demo.i5",1),
    new DictionaryService.SeedItem("v3","字典项3","config.dict.extend_dic_demo.i6",null),
    new DictionaryService.SeedItem("v31","字典项3-1","config.dict.extend_dic_demo.i7",6),
    new DictionaryService.SeedItem("v4","字典项4","config.dict.extend_dic_demo.i8",null)
   ))
  );
 }
}
