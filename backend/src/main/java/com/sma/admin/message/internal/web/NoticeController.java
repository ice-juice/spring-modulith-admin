package com.sma.admin.message.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.service.NoticeService;

@RestController @RequestMapping("/api/message/notices")
@com.sma.admin.common.request.GovernedApi(group="message.notices")
public class NoticeController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){
  // Only actor-owned operation metadata can replay. Actual content remains behind
  // the module's recipient/owner, tenant and portal checks on every domain call.
  com.sma.admin.common.security.CurrentUser.require();
 }
 private final NoticeService service;public NoticeController(NoticeService service){this.service=service;}
 public record Batch(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min=1,max=100) java.util.Set<@jakarta.validation.constraints.NotNull Long> ids){}
 @GetMapping @RequiresPermission("message:notice:view")
 public ApiResponse<PageResult<NoticeService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String keyword,
  @RequestParam(required=false)String type,@RequestParam(required=false)Integer priority,@RequestParam(required=false)String audienceType,@RequestParam(defaultValue="false")boolean trash){return ApiResponse.ok(service.page(page,keyword,type,priority,audienceType,trash));}
 @GetMapping("/{id}") @RequiresPermission("message:notice:view") public ApiResponse<NoticeService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
 @PostMapping @RequiresPermission("message:notice:create") public ApiResponse<NoticeService.View> create(@Valid @RequestBody NoticeService.Input input){return ApiResponse.ok(service.save(null,input));}
 @PutMapping("/{id}") @RequiresPermission("message:notice:update") public ApiResponse<NoticeService.View> update(@PathVariable long id,@Valid @RequestBody NoticeService.Input input){return ApiResponse.ok(service.save(id,input));}
 @DeleteMapping("/{id}") @RequiresPermission("message:notice:delete") public ApiResponse<Void> delete(@PathVariable long id){service.delete(id);return ApiResponse.ok();}
 @PutMapping("/{id}/restore") @RequiresPermission("message:notice:restore") public ApiResponse<Void> restore(@PathVariable long id){service.restore(id);return ApiResponse.ok();}
 @PostMapping("/batch-delete") @RequiresPermission("message:notice:delete") public ApiResponse<Void> deleteMany(@Valid @RequestBody Batch input){service.deleteMany(input.ids());return ApiResponse.ok();}
 @PostMapping("/batch-restore") @RequiresPermission("message:notice:restore") public ApiResponse<Void> restoreMany(@Valid @RequestBody Batch input){service.restoreMany(input.ids());return ApiResponse.ok();}
 @GetMapping("/{id}/readers") @RequiresPermission("message:notice:view") public ApiResponse<PageResult<NoticeService.Reader>> readers(@PathVariable long id,@ModelAttribute PageQuery page){return ApiResponse.ok(service.readers(id,page));}
 @GetMapping("/audience") @RequiresPermission("message:notice:view")
 public ApiResponse<List<OptionItem>> options(@RequestParam String type,@RequestParam(required=false)String keyword){return ApiResponse.ok(service.options(type,keyword));}
 @GetMapping("/inbox") @PermitLoggedIn public ApiResponse<PageResult<NoticeService.View>> inbox(@ModelAttribute PageQuery page,@RequestParam(required=false)Boolean unread){return ApiResponse.ok(service.inbox(page,unread));}
 @GetMapping("/inbox/{id}") @PermitLoggedIn public ApiResponse<NoticeService.View> inboxNotice(@PathVariable long id){return ApiResponse.ok(service.inboxNotice(id));}
 @com.sma.admin.common.request.GovernedApi(group="message.notices",ownResource=true)
 @PutMapping("/inbox/{id}/read") @PermitLoggedIn public ApiResponse<NoticeService.View> read(@PathVariable long id){return ApiResponse.ok(service.read(id));}
 @com.sma.admin.common.request.GovernedApi(group="message.notices",ownResource=true)
 @PutMapping("/inbox/read-all") @PermitLoggedIn public ApiResponse<Integer> readAll(){return ApiResponse.ok(service.readAll());}
 @com.sma.admin.common.request.GovernedApi(group="message.notices",ownResource=true)
 @PutMapping("/inbox/read-many") @PermitLoggedIn public ApiResponse<Void> readMany(@Valid @RequestBody Batch input){service.readMany(input.ids());return ApiResponse.ok();}
}
