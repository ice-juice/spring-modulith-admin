package com.sma.release.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.sma.release.auth.MachineManagement;

@RestController
@RequestMapping("/release/v1/security/machines")
public class MachineManagementController {
    private final MachineManagement machines;
    public MachineManagementController(MachineManagement machines){this.machines=machines;}
    @GetMapping ApiReply<List<MachineManagement.View>> list(@RequestHeader("Authorization")String authorization,
            @RequestParam(defaultValue="0")int offset,@RequestParam(defaultValue="100")int limit) {
        return ApiReply.ok(machines.list(ReleaseController.token(authorization),offset,limit));
    }
    @PostMapping("/{id}/token") ApiReply<MachineManagement.TokenDelivery> delivery(@RequestHeader("Authorization")String authorization,@PathVariable String id) {
        return ApiReply.ok(machines.delivery(ReleaseController.token(authorization),id));
    }
}
