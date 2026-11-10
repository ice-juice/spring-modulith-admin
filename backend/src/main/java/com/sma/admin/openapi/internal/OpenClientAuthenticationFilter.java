package com.sma.admin.openapi.internal;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.*;
import com.sma.admin.openapi.internal.service.*;
final class OpenClientAuthenticationFilter extends OncePerRequestFilter {
 private final OpenAuthenticationService authentication;private final ErrorResponseWriter errors;
 OpenClientAuthenticationFilter(OpenAuthenticationService authentication,ErrorResponseWriter errors){this.authentication=authentication;this.errors=errors;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  byte[] body=request.getInputStream().readNBytes(65537);if(body.length>65536){errors.write(request,response,CommonErrorCode.PAYLOAD_TOO_LARGE);return;}
  String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  var wrapped=new BufferedBodyRequest(request,body);var proof=new RequestProof(request.getMethod(),request.getRequestURI(),request.getQueryString(),request.getHeader("X-Timestamp"),request.getHeader("X-Nonce"),request.getHeader("X-Signature"),request.getHeader("X-App-Id"),hash);wrapped.setAttribute("sma.openapi-proof",proof);
  if(request.getRequestURI().equals("/open/token")){chain.doFilter(wrapped,response);return;}
  try{
   String header=request.getHeader("Authorization");var resolved=authentication.authenticate(header!=null&&header.startsWith("Bearer ")?header.substring(7):null,proof);var client=resolved.client();
   var principal=SmaPrincipal.forApplication(client.tenantId(),client.portal(),client.appId());
   var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));SecurityContextHolder.setContext(context);
   wrapped.setAttribute("sma.openapi-client",client);wrapped.setAttribute("sma.openapi-token",resolved.tokenId());chain.doFilter(wrapped,response);
  }catch(BizException invalid){errors.write(request,response,invalid.getErrorCode());}
 }
}
