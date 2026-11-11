package com.sma.admin.mail.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class SmtpNetworkPolicyTests {
    private SmtpProperties settings(Set<String> privateIps){return new SmtpProperties(List.of(new SmtpProperties.Route("smtp.example.test",Set.of(587),privateIps)),null,null,null,null,1);}
    private SmtpNetworkPolicy policy(Set<String> privateIps,String... addresses){return new SmtpNetworkPolicy(settings(privateIps)){
        @Override protected InetAddress[] resolve(String host)throws UnknownHostException{
            var list=new ArrayList<InetAddress>();for(String ip:addresses)list.add(InetAddress.getByName(ip));return list.toArray(InetAddress[]::new);
        }
    };}
    @Test void exactHostAndPortAreRequiredBeforeDns(){
        var policy=policy(Set.of(),"8.8.8.8");
        assertThatThrownBy(()->policy.authorize("smtp.example.test.evil.test",587)).isInstanceOf(SmtpNetworkPolicy.PolicyDenied.class);
        assertThatThrownBy(()->policy.authorize("smtp.example.test",25)).isInstanceOf(SmtpNetworkPolicy.PolicyDenied.class);
    }
    @Test void privateMixedAndMetadataAnswersAreRejected()throws Exception{
        for(String ip:List.of("127.0.0.1","10.1.2.3","172.16.0.1","192.168.1.1","169.254.169.254","100.64.0.1","::1","fd00::1","0.0.0.0"))
            assertThatThrownBy(()->policy(Set.of(),"8.8.8.8",ip).authorize("smtp.example.test",587)).isInstanceOf(SmtpNetworkPolicy.PolicyDenied.class);
        assertThatThrownBy(()->policy(Set.of("169.254.169.254"),"169.254.169.254").authorize("smtp.example.test",587)).isInstanceOf(SmtpNetworkPolicy.PolicyDenied.class);
        assertThat(policy(Set.of("127.0.0.1"),"127.0.0.1").authorize("smtp.example.test",587).isLoopbackAddress()).isTrue();
    }
    @Test void pinnedSocketDoesNotConnectToTheAddressSuppliedLater()throws Exception{
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());var factory=new PinnedSmtpSocketFactory(InetAddress.getLoopbackAddress(),server.getLocalPort())){
            server.setSoTimeout(2000);var socket=factory.createSocket();
            socket.connect(new InetSocketAddress("192.0.2.1",1),1000);
            try(var accepted=server.accept()){assertThat(socket.getPort()).isEqualTo(server.getLocalPort());}
            factory.close();assertThatThrownBy(factory::createSocket).isInstanceOf(java.io.IOException.class);
        }
    }
    @Test void unsafeConfigurationAndUnboundedTimeoutsFailAtConstruction(){
        assertThatThrownBy(()->new SmtpProperties.Route("*.example.test",Set.of(587),Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SmtpProperties(List.of(),null,null,null,Duration.ofHours(1),1)).isInstanceOf(IllegalArgumentException.class);
    }
}
