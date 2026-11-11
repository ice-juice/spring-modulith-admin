package com.sma.worker;

import java.net.URI;
import java.nio.file.Path;
import com.sma.extension.protocol.Checks;

/** Operator configuration only. No executable, image, network option or filesystem root comes from an upload. */
public record WorkerSettings(URI securityUrl,String token,String environment,Path workDirectory,Path dockerExecutable,
        String scannerImage,String javaImage,Path scannerDatabase,String containerUser) {
    public WorkerSettings {
        Checks.require(securityUrl!=null && "https".equals(securityUrl.getScheme()) && securityUrl.getHost()!=null
                && securityUrl.getUserInfo()==null && securityUrl.getQuery()==null && securityUrl.getFragment()==null,"worker-https-required");
        Checks.require(token!=null && token.length()>=32,"worker-token-required");Checks.id(environment);
        Checks.require(workDirectory!=null && dockerExecutable!=null && scannerDatabase!=null,"worker-paths-required");
        for(String image:java.util.List.of(scannerImage,javaImage)) Checks.require(image.matches("[a-zA-Z0-9][a-zA-Z0-9./:_-]*@sha256:[0-9a-f]{64}"),"pinned-image-required");
        Checks.require(containerUser!=null && containerUser.matches("[1-9][0-9]{0,8}:[1-9][0-9]{0,8}"),"nonroot-container-user-required");
    }
    @Override public String toString() {return "WorkerSettings[environment="+environment+", secrets=redacted]";}
}
