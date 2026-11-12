package com.sma.admin.message.internal.service;
import org.springframework.stereotype.Component;
import com.sma.admin.common.deployment.*;
@Component
public class SocketPolicyEvidence implements DeploymentEvidenceSource {
    private final SocketProperties socket;
    public SocketPolicyEvidence(SocketProperties socket){this.socket=socket;}
    @Override public String id(){return "message.socket-policy";}
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge){
        DeploymentEvidence.requireHost();
        return new DeploymentFields().text("protocol","1").duration("ticket-ttl",socket.ticketTtl())
                .values("allowed-origins",socket.allowedOrigins()).number("maximum-connections",socket.maxConnections()).proof(id(),challenge);
    }
}
