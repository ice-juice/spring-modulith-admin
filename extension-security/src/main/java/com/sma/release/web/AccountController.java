package com.sma.release.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.sma.release.auth.AccountManagement;
import com.sma.release.auth.SecurityRecords;

@RestController @RequestMapping("/release/v1/accounts")
public class AccountController {
    private final AccountManagement accounts;
    public AccountController(AccountManagement accounts){this.accounts=accounts;}
    @GetMapping public ApiReply<List<AccountManagement.Account>> list(@RequestHeader("Authorization")String authorization,
            @RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="50")int limit){return ApiReply.ok(accounts.accounts(ReleaseController.token(authorization),offset,limit));}
    @PostMapping("/{user}/enrollment-ticket") public ApiReply<SecurityRecords.NewAccount> enrollment(@RequestHeader("Authorization")String authorization,@PathVariable String user){return ApiReply.ok(accounts.delivery(ReleaseController.token(authorization),user));}
    @GetMapping("/me/notices") public ApiReply<List<AccountManagement.Notice>> notices(@RequestHeader("Authorization")String authorization,
            @RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="50")int limit){return ApiReply.ok(accounts.notices(ReleaseController.token(authorization),offset,limit));}
}
