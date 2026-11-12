package com.sma.admin.mail.internal.service;
import java.util.Comparator;
import org.springframework.stereotype.Component;
import com.sma.admin.common.deployment.*;
@Component
public class MailPolicyEvidence implements DeploymentEvidenceSource {
    private final MailWorkProperties work;private final SmtpProperties smtp;
    public MailPolicyEvidence(MailWorkProperties work,SmtpProperties smtp){this.work=work;this.smtp=smtp;}
    @Override public String id(){return "mail.work-policy";}
    @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge){
        DeploymentEvidence.requireHost();
        var fields=new DeploymentFields().text("protocol","1").number("work.queued",work.maximumQueuedPerProfile()).number("work.attempts",work.maximumAttempts())
                .duration("work.lease",work.leaseDuration()).duration("smtp.connect",smtp.connectTimeout()).duration("smtp.read",smtp.readTimeout())
                .duration("smtp.write",smtp.writeTimeout()).duration("smtp.operation",smtp.operationTimeout()).number("smtp.concurrent",smtp.maximumConcurrent());
        var routes=smtp.routes().stream().sorted(Comparator.comparing(SmtpProperties.Route::host)).toList();fields.number("route-count",routes.size());
        for(int i=0;i<routes.size();i++){
            var route=routes.get(i);
            fields.text("route."+i,new DeploymentFields().text("host",route.host()).values("ports",route.ports().stream().map(String::valueOf).toList())
                    .values("private-addresses",route.approvedPrivateAddresses()).proof("mail.egress-route",challenge).digest());
        }
        return fields.proof(id(),challenge); // Per-tenant SMTP credentials remain in their owning durable records.
    }
}
