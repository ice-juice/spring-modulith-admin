package com.sma.release.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.sma.release.auth.*;

@RestController @RequestMapping("/release/v1")
public class ConsoleIdentityController {
    private final ConsoleIdentityService consoles;private final IdentityService identities;
    public ConsoleIdentityController(ConsoleIdentityService consoles,IdentityService identities){this.consoles=consoles;this.identities=identities;}
    public record EnrollmentProof(String operationPassword,String enrollmentToken,String otp) {
        @Override public String toString(){return "EnrollmentProof[redacted]";}
    }
    public record RecoveryProof(String operationPassword,String recoveryCode) {
        @Override public String toString(){return "RecoveryProof[redacted]";}
    }
    @PostMapping("/console/recovery") public ApiReply<SecurityRecords.NewAccount> recover(@RequestHeader("Authorization")String auth,@RequestBody RecoveryProof input){return ApiReply.ok(identities.recoverConsoleFactor(ReleaseController.token(auth),input.operationPassword(),input.recoveryCode()));}
    @PostMapping("/machines/consoles/sessions") public ApiReply<SecurityRecords.LoginTicket> exchange(@RequestHeader("Authorization")String auth,@RequestBody ConsoleIdentityService.Exchange input) {
        return ApiReply.ok(consoles.exchange(ReleaseController.token(auth),input));
    }
    @GetMapping("/console/identity") public ApiReply<ConsoleIdentityService.Status> status(@RequestHeader("Authorization")String auth){return ApiReply.ok(consoles.status(ReleaseController.token(auth)));}
    @GetMapping("/console/bindings") public ApiReply<List<ConsoleIdentityService.Binding>> bindings(@RequestHeader("Authorization")String auth,@RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="50")int limit){return ApiReply.ok(consoles.bindings(ReleaseController.token(auth),offset,limit));}
    @PostMapping("/console/enrollment/begin") public ApiReply<SecurityRecords.Enrollment> begin(@RequestHeader("Authorization")String auth,@RequestBody EnrollmentProof input){return ApiReply.ok(identities.beginConsoleEnrollment(ReleaseController.token(auth),input.operationPassword(),input.enrollmentToken()));}
    @PostMapping("/console/enrollment/complete") public ApiReply<List<String>> complete(@RequestHeader("Authorization")String auth,@RequestBody EnrollmentProof input){return ApiReply.ok(identities.completeConsoleEnrollment(ReleaseController.token(auth),input.operationPassword(),input.enrollmentToken(),input.otp()));}
}
