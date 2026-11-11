package com.sma.admin.mail.internal.service;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;

/** Angus connects this socket by name, but the actual connection uses only the approved address. */
final class PinnedSmtpSocketFactory extends SocketFactory implements AutoCloseable {
    private final InetAddress address;private final int port;
    private final AtomicReference<Socket> socket=new AtomicReference<>();
    private volatile boolean closed;
    PinnedSmtpSocketFactory(InetAddress address,int port){this.address=address;this.port=port;}
    @Override public Socket createSocket()throws IOException{
        if(closed)throw new IOException("SMTP operation expired");
        var created=new Socket(){
            @Override public void connect(SocketAddress ignored,int timeout)throws IOException {
                if(closed)throw new IOException("SMTP operation expired");
                super.connect(new InetSocketAddress(address,port),timeout);
            }
            @Override public void connect(SocketAddress ignored)throws IOException {connect(ignored,5000);}
        };
        if(!socket.compareAndSet(null,created)){created.close();throw new IOException("SMTP reconnect is not allowed");}
        if(closed){created.close();throw new IOException("SMTP operation expired");}return created;
    }
    @Override public Socket createSocket(String host,int port)throws IOException {var s=createSocket();s.connect(new InetSocketAddress(address,this.port));return s;}
    @Override public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException {var s=createSocket();s.bind(new InetSocketAddress(local,localPort));s.connect(new InetSocketAddress(address,this.port));return s;}
    @Override public Socket createSocket(InetAddress host,int port)throws IOException{return createSocket("",port);}
    @Override public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return createSocket("",port,local,localPort);}
    @Override public void close(){closed=true;var current=socket.get();if(current!=null)try{current.close();}catch(IOException ignored){}}
}
