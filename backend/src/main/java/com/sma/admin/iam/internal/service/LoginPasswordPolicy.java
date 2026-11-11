package com.sma.admin.iam.internal.service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.BizException;
import com.sma.admin.iam.IamErrorCode;

/** Write policy only: never applied to legacy password verification. */
@Component
public class LoginPasswordPolicy implements com.sma.admin.common.security.PasswordStrengthPolicy {
    private final Set<String> weak;
    public LoginPasswordPolicy(){
        try(var words=getClass().getResourceAsStream("/iam/password-policy/weak-passwords.txt");var checksum=getClass().getResourceAsStream("/iam/password-policy/weak-passwords.sha256")){
            if(words==null||checksum==null)throw new IllegalStateException("Required login password policy resources missing");
            byte[] bytes=words.readAllBytes();String expected=new String(checksum.readAllBytes(),StandardCharsets.US_ASCII).trim();
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!actual.equals(expected))throw new IllegalStateException("Login password blocklist checksum mismatch");
            weak=Set.copyOf(new String(bytes,StandardCharsets.UTF_8).lines().filter(s->!s.isBlank()).toList());
            if(weak.size()<2000||!weak.containsAll(List.of("password1","admin123","qwerty12","abc12345","123456ab")))throw new IllegalStateException("Incomplete login password blocklist");
        }catch(IOException|java.security.GeneralSecurityException invalid){throw new IllegalStateException("Cannot load login password policy");}
    }
    public void validate(String username,String candidate){
        var decision=evaluate(username,candidate);
        if(decision==Decision.INVALID_FORMAT)throw new BizException(IamErrorCode.PASSWORD_FORMAT);
        if(decision==Decision.WEAK)throw new BizException(IamErrorCode.WEAK_PASSWORD);
    }
    @Override public Decision evaluate(String username,String candidate){
        if(username==null||candidate==null||!candidate.matches("[A-Za-z0-9]{8,12}")||!candidate.matches(".*[A-Za-z].*")||!candidate.matches(".*[0-9].*"))return Decision.INVALID_FORMAT;
        String value=candidate.toLowerCase(Locale.ROOT),name=username.toLowerCase(Locale.ROOT);
        boolean related=value.equals(name)||value.matches(java.util.regex.Pattern.quote(name)+"[0-9]{1,4}")||value.matches("[0-9]{1,4}"+java.util.regex.Pattern.quote(name));
        boolean repeated=false;
        for(int length=1;length<=4;length++)if(value.length()%length==0&&value.substring(0,length).repeat(value.length()/length).equals(value))repeated=true;
        boolean runs=value.matches("([a-z])\\1{2,}([0-9])\\2{2,}")||value.matches("([0-9])\\1{2,}([a-z])\\2{2,}");
        return weak.contains(value)||related||repeated||runs?Decision.WEAK:Decision.ACCEPTED;
    }
}
