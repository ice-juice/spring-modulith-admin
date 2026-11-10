package com.sma.admin.openapi.internal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
final class BufferedBodyRequest extends HttpServletRequestWrapper {
 private final byte[] body;BufferedBodyRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
 @Override public int getContentLength(){return body.length;}@Override public long getContentLengthLong(){return body.length;}
 @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Blocking request body");}};}
 @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
}
