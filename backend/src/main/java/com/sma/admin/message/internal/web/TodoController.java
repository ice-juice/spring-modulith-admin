package com.sma.admin.message.internal.web;
import java.time.Instant;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.service.TodoService;

@RestController @RequestMapping("/api/message/todos") @PermitLoggedIn
public class TodoController {
 private final TodoService service;public TodoController(TodoService service){this.service=service;}
 public record State(@NotNull @Min(1) @Max(5) Integer state){}
 public record Batch(@NotNull @Size(min=1,max=100) Set<@NotNull Long> ids){}
 public record BatchState(@NotNull @Size(min=1,max=100) Set<@NotNull Long> ids,@NotNull @Min(1) @Max(5) Integer state){}
 @GetMapping public ApiResponse<PageResult<TodoService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword,
  @RequestParam(required=false)Integer state,@RequestParam(required=false)Integer type,@RequestParam(required=false)Integer priority,@RequestParam(required=false)Boolean active,
  @RequestParam(required=false)String dateField,@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to){return ApiResponse.ok(service.page(page,keyword,state,type,priority,active,dateField,from,to,false));}
 @GetMapping("/managed") @RequiresPermission("message:todo:view")
 public ApiResponse<PageResult<TodoService.View>> managed(@ModelAttribute PageQuery page){return ApiResponse.ok(service.page(page,null,null,null,null,null,null,null,null,true));}
 @GetMapping("/{id}") public ApiResponse<TodoService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
 @GetMapping("/recipients") @RequiresPermission("message:todo:assign") public ApiResponse<java.util.List<OptionItem>> recipients(@RequestParam(required=false)String keyword){return ApiResponse.ok(service.recipients(keyword));}
 @PostMapping @AuditAction(value="message:todo:create",path="/message/inbox") public ApiResponse<TodoService.View> create(@Valid @RequestBody TodoService.Input input){return ApiResponse.ok(service.save(null,input,false));}
 @PostMapping("/assign") @RequiresPermission("message:todo:assign") public ApiResponse<TodoService.View> assign(@Valid @RequestBody TodoService.Input input){return ApiResponse.ok(service.save(null,input,true));}
 @PutMapping("/{id}") @AuditAction(value="message:todo:update",path="/message/inbox") public ApiResponse<TodoService.View> update(@PathVariable long id,@Valid @RequestBody TodoService.Input input){return ApiResponse.ok(service.save(id,input,false));}
 @PutMapping("/{id}/state") @AuditAction(value="message:todo:state",path="/message/inbox") public ApiResponse<TodoService.View> state(@PathVariable long id,@Valid @RequestBody State input){return ApiResponse.ok(service.state(id,input.state()));}
 @PutMapping("/{id}/read") public ApiResponse<TodoService.View> read(@PathVariable long id){return ApiResponse.ok(service.read(id));}
 @PostMapping("/batch-state") @AuditAction(value="message:todo:state",path="/message/inbox") public ApiResponse<Void> stateMany(@Valid @RequestBody BatchState input){service.batchState(input.ids(),input.state());return ApiResponse.ok();}
 @DeleteMapping("/{id}") @AuditAction(value="message:todo:delete",path="/message/inbox") public ApiResponse<Void> delete(@PathVariable long id){service.deleteMany(Set.of(id));return ApiResponse.ok();}
 @PostMapping("/batch-delete") @AuditAction(value="message:todo:delete",path="/message/inbox") public ApiResponse<Void> deleteMany(@Valid @RequestBody Batch input){service.deleteMany(input.ids());return ApiResponse.ok();}
}
