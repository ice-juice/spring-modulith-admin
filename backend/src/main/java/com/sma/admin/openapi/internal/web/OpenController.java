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
 private final OpenAuthenticationService service;private final com.sma.admin.common.extension.ExtensionApplicationGateway extensions;
 private final com.sma.admin.mail.MailReceiptApi mailReceipts;
 public OpenController(OpenAuthenticationService service,com.sma.admin.common.extension.ExtensionApplicationGateway extensions,com.sma.admin.mail.MailReceiptApi mailReceipts){this.service=service;this.extensions=extensions;this.mailReceipts=mailReceipts;}
 @PostMapping("/mail/receipts") @PermitMachine
 public ApiResponse<com.sma.admin.mail.MailReceiptApi.Result> mailReceipt(@RequestBody com.sma.admin.mail.MailReceiptApi.Command input){return ApiResponse.ok(mailReceipts.accept(input));}
 @PostMapping("/extensions/{deployment}/{plugin}/{entry}") @PermitMachine
 public ApiResponse<com.sma.admin.common.extension.ExtensionApplicationGateway.Result> extension(@PathVariable String deployment,@PathVariable String plugin,@PathVariable String entry,
   @RequestHeader(value="Idempotency-Key",required=false)String key,jakarta.servlet.http.HttpServletRequest request)throws java.io.IOException {
   byte[] input=request.getInputStream().readNBytes(65537);if(input.length>65536)throw new com.sma.admin.common.BizException(com.sma.admin.common.CommonErrorCode.PAYLOAD_TOO_LARGE);
   return ApiResponse.ok(extensions.invoke(deployment,plugin,entry,key,input));
 }
 @PostMapping("/token") @PermitAnonymous public ApiResponse<OpenTokenIssuer.Ticket> token(@Valid @RequestBody OpenAuthenticationService.Credentials input,@RequestAttribute("sma.openapi-proof")RequestProof proof,jakarta.servlet.http.HttpServletRequest request){return ApiResponse.ok(service.issue(input,proof,request.getRemoteAddr()));}
 @GetMapping("/me") @PermitMachine public ApiResponse<ClientIdentity> me(@RequestAttribute("sma.openapi-client")ClientIdentity client){return ApiResponse.ok(client);}
 @PostMapping("/echo") @PermitMachine public ApiResponse<Map<String,Object>> echo(@RequestBody(required=false)Map<String,Object> body,@RequestAttribute("sma.openapi-client")ClientIdentity client){return ApiResponse.ok(Map.of("client",client,"payload",body==null?Map.of():body));}
 @PostMapping("/revoke") @PermitMachine public ApiResponse<Void> revoke(@RequestAttribute("sma.openapi-token")long id){service.revoke(id);return ApiResponse.ok();}
}
