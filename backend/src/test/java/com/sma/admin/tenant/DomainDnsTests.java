package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.tenant.internal.service.JndiDomainDns;

class DomainDnsTests {
    @Test void actualDnsTransportReadsTxtFollowsBoundedAliasesAndReturnsLiterals() throws Exception {
        try(var fixture=new Fixture()) {
            var dns=new JndiDomainDns("dns://127.0.0.1:"+fixture.port()+"/");
            assertThat(dns.txt("_sma-tenant.p10.example.test",Duration.ofSeconds(2))).anySatisfy(value->assertThat(value).isIn(Fixture.TOKEN,'"'+Fixture.TOKEN+'"'));
            assertThat(dns.addresses("p10.example.test",Duration.ofSeconds(2))).extracting(InetAddress::getHostAddress).containsExactly("127.0.0.1","0:0:0:0:0:0:0:1");
            assertThat(fixture.queries).contains("p10.example.test","edge.example.test");
            assertThatThrownBy(()->dns.addresses("cycle.example.test",Duration.ofSeconds(2))).isInstanceOf(javax.naming.NamingException.class);
        }
    }
    private static class Fixture implements AutoCloseable {
        static final String TOKEN="sma-tenant-v1="+"a".repeat(43);
        final DatagramSocket socket=new DatagramSocket(new InetSocketAddress("127.0.0.1",0));
        final ExecutorService executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("p10-dns-fixture").factory());
        final List<String> queries=new CopyOnWriteArrayList<>();
        Fixture() throws SocketException {
            executor.submit(()->{
                while(!socket.isClosed()) try {
                    var packet=new DatagramPacket(new byte[2048],2048);socket.receive(packet);
                    byte[] response=answer(Arrays.copyOf(packet.getData(),packet.getLength()));
                    socket.send(new DatagramPacket(response,response.length,packet.getSocketAddress()));
                } catch(SocketException closed) { if(!socket.isClosed())throw new IllegalStateException(closed); }
                catch(IOException invalid) { throw new IllegalStateException(invalid); }
            });
        }
        int port() { return socket.getLocalPort(); }
        private byte[] answer(byte[] query) throws IOException {
            int cursor=12;List<String> labels=new ArrayList<>();
            while(cursor<query.length && query[cursor]!=0) { int size=Byte.toUnsignedInt(query[cursor++]);if(size>63 || cursor+size>query.length)throw new IOException("Invalid fixture question");labels.add(new String(query,cursor,size,StandardCharsets.US_ASCII));cursor+=size; }
            cursor++;int questionEnd=cursor+4;if(questionEnd>query.length)throw new IOException("Short fixture question");
            String name=String.join(".",labels);queries.add(name);
            List<Record> records;
            if(name.startsWith("_sma-tenant.")) {byte[] text=TOKEN.getBytes(StandardCharsets.US_ASCII);var data=new ByteArrayOutputStream();data.write(text.length);data.write(text);records=List.of(new Record(16,data.toByteArray()));}
            else if(name.equals("p10.example.test")) records=List.of(new Record(5,name("edge.example.test")));
            else if(name.equals("cycle.example.test")) records=List.of(new Record(5,name("cycle.example.test")));
            else records=List.of(new Record(1,new byte[]{127,0,0,1}),new Record(28,new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1}));
            var output=new ByteArrayOutputStream();var wire=new DataOutputStream(output);
            wire.write(query,0,2);wire.writeShort(0x8180);wire.writeShort(1);wire.writeShort(records.size());wire.writeShort(0);wire.writeShort(0);
            wire.write(query,12,questionEnd-12);
            for(var record:records) {wire.writeShort(0xc00c);wire.writeShort(record.type());wire.writeShort(1);wire.writeInt(30);wire.writeShort(record.data().length);wire.write(record.data());}
            return output.toByteArray();
        }
        private byte[] name(String value) throws IOException {var output=new ByteArrayOutputStream();for(String label:value.split("\\.")){byte[] bytes=label.getBytes(StandardCharsets.US_ASCII);output.write(bytes.length);output.write(bytes);}output.write(0);return output.toByteArray();}
        private record Record(int type,byte[] data) {}
        @Override public void close() throws Exception {socket.close();executor.shutdownNow();if(!executor.awaitTermination(3,TimeUnit.SECONDS))throw new IllegalStateException("DNS fixture did not stop");}
    }
}
