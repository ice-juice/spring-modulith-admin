package com.sma.admin.workspace.internal.web;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.PermitLoggedIn;
import com.sma.admin.common.security.AuditAction;
import com.sma.admin.workspace.internal.service.ShelfService;

@RestController @RequestMapping("/api/workspace/shelf")
@com.sma.admin.common.request.GovernedApi(group="workspace.shelf",ownResource=true)
public class ShelfController implements com.sma.admin.common.request.RequestAuthorizer {
 @Override public void authorizeRequest(String method,java.util.Map<String,String> variables){
  // Only actor-owned operation metadata can replay. Actual content remains behind
  // the module's recipient/owner, tenant and portal checks on every domain call.
  com.sma.admin.common.security.CurrentUser.require();
 }
 @ModelAttribute public void privateResponse(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 private final ShelfService service;
 public ShelfController(ShelfService service){this.service=service;}
 @GetMapping @PermitLoggedIn public ApiResponse<ShelfService.Board> board(){return ApiResponse.ok(service.board());}
 @PostMapping("/bookmarks") @PermitLoggedIn @AuditAction(value="workspace:shelf:bookmark",path="/") public ApiResponse<ShelfService.Board> add(@Valid @RequestBody ShelfService.BookmarkInput input){return ApiResponse.ok(service.addBookmark(input));}
 @PutMapping("/bookmarks/{id}") @PermitLoggedIn @AuditAction(value="workspace:shelf:bookmark",path="/") public ApiResponse<ShelfService.Board> edit(@PathVariable long id,@Valid @RequestBody ShelfService.BookmarkEdit input){return ApiResponse.ok(service.editBookmark(id,input));}
 @DeleteMapping("/bookmarks/{id}") @PermitLoggedIn @AuditAction(value="workspace:shelf:bookmark",path="/") public ApiResponse<ShelfService.Board> remove(@PathVariable long id,@RequestParam long version){return ApiResponse.ok(service.removeBookmark(id,version));}
 @PutMapping("/bookmarks/order") @PermitLoggedIn @AuditAction(value="workspace:shelf:bookmark",path="/") public ApiResponse<ShelfService.Board> order(@Valid @RequestBody ShelfService.Order input){return ApiResponse.ok(service.reorder(input));}
 @PutMapping("/mode") @PermitLoggedIn @AuditAction(value="workspace:shelf:settings",path="/") public ApiResponse<ShelfService.Board> mode(@Valid @RequestBody ShelfService.Mode input){return ApiResponse.ok(service.mode(input));}
 @GetMapping("/notes") @PermitLoggedIn public ApiResponse<List<ShelfService.Note>> notes(){return ApiResponse.ok(service.notes());}
 @GetMapping("/notes/{id}") @PermitLoggedIn public ApiResponse<ShelfService.Note> note(@PathVariable long id){return ApiResponse.ok(service.getNote(id));}
 @PostMapping("/notes") @PermitLoggedIn @AuditAction(value="workspace:shelf:note",path="/") public ApiResponse<ShelfService.Note> create(@Valid @RequestBody ShelfService.CreateNote input){return ApiResponse.ok(service.create(input));}
 @PutMapping("/notes/{id}") @PermitLoggedIn @AuditAction(value="workspace:shelf:note",path="/") public ApiResponse<ShelfService.Note> update(@PathVariable long id,@Valid @RequestBody ShelfService.UpdateNote input){return ApiResponse.ok(service.update(id,input));}
 @PutMapping("/notes/{id}/status") @PermitLoggedIn @AuditAction(value="workspace:shelf:note",path="/") public ApiResponse<ShelfService.Note> status(@PathVariable long id,@Valid @RequestBody ShelfService.Transition input){return ApiResponse.ok(service.transition(id,input));}
}
