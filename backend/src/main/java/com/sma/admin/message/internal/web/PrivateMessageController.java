package com.sma.admin.message.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.service.PrivateMessageService;
@RestController @RequestMapping("/api/message/private") @PermitLoggedIn
public class PrivateMessageController {
 private final PrivateMessageService service;public PrivateMessageController(PrivateMessageService service){this.service=service;}
 @PostMapping public ApiResponse<PrivateMessageService.View> send(@Valid @RequestBody PrivateMessageService.Input input){return ApiResponse.ok(service.send(input));}
 @GetMapping public ApiResponse<PageResult<PrivateMessageService.View>> page(@ModelAttribute PageQuery page,@RequestParam(required=false)String box,@RequestParam(required=false)Long peerId,@RequestParam(required=false)Boolean unread){return ApiResponse.ok(service.page(page,box,peerId,unread));}
 @GetMapping("/{id}") public ApiResponse<PrivateMessageService.View> get(@PathVariable long id){return ApiResponse.ok(service.get(id));}
 @PutMapping("/{id}/read") public ApiResponse<PrivateMessageService.View> read(@PathVariable long id){return ApiResponse.ok(service.read(id));}
 @PutMapping("/read-all") public ApiResponse<Integer> readAll(){return ApiResponse.ok(service.readAll());}
 @GetMapping("/recipients") public ApiResponse<List<OptionItem>> recipients(@RequestParam(required=false)String keyword){return ApiResponse.ok(service.recipients(keyword));}
}
