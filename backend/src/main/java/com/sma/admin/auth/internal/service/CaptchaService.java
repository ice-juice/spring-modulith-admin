package com.sma.admin.auth.internal.service;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.AuthErrorCode;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.BizException;

@Service
public class CaptchaService {
    private static final String SYMBOLS="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final CaptchaMapper mapper;
    private final SecureRandom random;
    private final Clock clock;
    private final TenantAuthenticationGuard tenants;
    @org.springframework.beans.factory.annotation.Autowired
    public CaptchaService(CaptchaMapper mapper,TenantAuthenticationGuard tenants) { this(mapper,new SecureRandom(),Clock.systemUTC(),tenants); }
    CaptchaService(CaptchaMapper mapper,SecureRandom random,Clock clock,TenantAuthenticationGuard tenants) { this.mapper=mapper; this.random=random; this.clock=clock;this.tenants=tenants; }
    public record Challenge(String id,String image) {}
    public Challenge issue() {
        long generation=tenants.capture(com.sma.admin.common.security.CurrentUser.tenantId(),com.sma.admin.common.security.CurrentUser.portal());
        mapper.removeExpired(clock.instant());
        StringBuilder answer=new StringBuilder();
        for(int i=0;i<5;i++) answer.append(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));
        String id=UUID.randomUUID().toString();
        var row=new CaptchaEntity(); row.setChallengeId(id); row.setAnswerHash(Tokens.hash(id+":"+answer)); row.setExpiresAt(clock.instant().plusSeconds(120));
        row.setPortal(com.sma.admin.common.security.CurrentUser.portal());row.setTenantSecurityGeneration(generation);
        mapper.insert(row);
        return new Challenge(id,render(answer.toString()));
    }
    public void verify(String id,String answer) {
        if(id==null||answer==null) throw new BizException(AuthErrorCode.INVALID_CAPTCHA);
        var row=mapper.selectOne(new LambdaQueryWrapper<CaptchaEntity>().eq(CaptchaEntity::getChallengeId,id));
        // Atomic single-use consume. Wrong or expired answers also consume the challenge.
        if(row==null||!tenants.active(row.getTenantId(),row.getPortal(),row.getTenantSecurityGeneration())
                ||mapper.deleteById(row.getId())!=1||!row.getExpiresAt().isAfter(clock.instant())
                ||!java.security.MessageDigest.isEqual(row.getAnswerHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                Tokens.hash(id+":"+answer.trim().toUpperCase(Locale.ROOT)).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new BizException(AuthErrorCode.INVALID_CAPTCHA);
        }
    }
    private String render(String answer) {
        BufferedImage image=new BufferedImage(150,44,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();
        try {
            graphics.setColor(new Color(235,243,245)); graphics.fillRect(0,0,150,44);
            for(int i=0;i<10;i++) { graphics.setColor(new Color(140+random.nextInt(70),180,180)); graphics.drawLine(random.nextInt(150),random.nextInt(44),random.nextInt(150),random.nextInt(44)); }
            graphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,27));
            for(int i=0;i<answer.length();i++) { graphics.setColor(new Color(20+random.nextInt(60),60+random.nextInt(60),80)); graphics.drawString(String.valueOf(answer.charAt(i)),10+i*27,31+random.nextInt(5)); }
            var output=new ByteArrayOutputStream(); ImageIO.write(image,"png",output);
            return "data:image/png;base64,"+Base64.getEncoder().encodeToString(output.toByteArray());
        } catch(IOException ex) { throw new UncheckedIOException(ex); }
        finally { graphics.dispose(); }
    }
}
