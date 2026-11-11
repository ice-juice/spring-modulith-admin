package com.sma.admin.message.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.service.PrivateMessageService;
@RestController @RequestMapping("/api/message/private") @PermitLoggedIn
@com.sma.admin.common.request.GovernedApi(group="message.private",ownResource=true)
public class PrivateMessageController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){
  // Only actor-owned operation metadata can replay. Actual content remains behind
  // the module's recipient/owner, tenant and portal checks on every domain call.
  com.sma.admin.common.security.CurrentUser.require();
 }
 private final PrivateMessageService service;public PrivateMessageController(PrivateMessageService service){this.service=service;}
 @PostMapping public ApiResponse<PrivateMessageService.View> send(@Valid @RequestBody PrivateMessageService.Input input){return ApiResponse.ok(service.send(input));}
 @GetMapping public ApiResponse<PageResult<PrivateMessageService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String box,@RequestParam(required=false)Long peerId,@RequestParam(required=false)Boolean unread){return ApiResponse.ok(service.page(page,box,peerId,unread));}
 @GetMapping("/{id}") public ApiResponse<PrivateMessageService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
 @PutMapping("/{id}/read") public ApiResponse<PrivateMessageService.View> read(@PathVariable long id){return ApiResponse.ok(service.read(id));}
 @PutMapping("/read-all") public ApiResponse<Integer> readAll(){return ApiResponse.ok(service.readAll());}
 @GetMapping("/recipients") public ApiResponse<List<OptionItem>> recipients(@RequestParam(required=false)String keyword){return ApiResponse.ok(service.recipients(keyword));}
}
