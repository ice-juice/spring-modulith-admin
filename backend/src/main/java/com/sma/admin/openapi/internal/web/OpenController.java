package com.sma.admin.openapi.internal.web;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.ClientIdentity;
import com.sma.admin.openapi.internal.service.*;
@RestController @RequestMapping("/open")
public class OpenController {
 private final OpenAuthenticationService service;public OpenController(OpenAuthenticationService service){this.service=service;}
 @PostMapping("/token") @PermitAnonymous public ApiResponse<OpenTokenIssuer.Ticket> token(@Valid @RequestBody OpenAuthenticationService.Credentials input,@RequestAttribute("sma.openapi-proof")RequestProof proof,jakarta.servlet.http.HttpServletRequest request){return ApiResponse.ok(service.issue(input,proof,request.getRemoteAddr()));}
 @GetMapping("/me") @PermitLoggedIn public ApiResponse<ClientIdentity> me(@RequestAttribute("sma.openapi-client")ClientIdentity client){return ApiResponse.ok(client);}
 @PostMapping("/echo") @PermitLoggedIn public ApiResponse<Map<String,Object>> echo(@RequestBody(required=false)Map<String,Object> body,@RequestAttribute("sma.openapi-client")ClientIdentity client){return ApiResponse.ok(Map.of("client",client,"payload",body==null?Map.of():body));}
 @PostMapping("/revoke") @PermitLoggedIn public ApiResponse<Void> revoke(@RequestAttribute("sma.openapi-token")long id){service.revoke(id);return ApiResponse.ok();}
}
