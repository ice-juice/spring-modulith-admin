package com.sma.admin.identity.internal.service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Tenant budget precedes every email challenge mutation. Verification rejection returns normally to preserve charges. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class IdentityEmailBudget {
    private final IdentityEmailBudgetMapper rows;private final IdentityEmailRateMapper rates;private final IdentityEmailProperties settings;
    private final IdentityClock clock;private final TenantSecretCodec secrets;
    public IdentityEmailBudget(IdentityEmailBudgetMapper rows,IdentityEmailRateMapper rates,IdentityEmailProperties settings,IdentityClock clock,TenantSecretCodec secrets){
        this.rows=rows;this.rates=rates;this.settings=settings;this.clock=clock;this.secrets=secrets;
    }
    IdentityEmailBudgetEntity lock(){
        IdentityAuthority.worker();rows.ensure(IdWorker.getId(),ActorContext.tenantId(),clock.now());return existing();
    }
    IdentityEmailBudgetEntity existing(){IdentityAuthority.worker();return rows.selectOne(new LambdaQueryWrapper<IdentityEmailBudgetEntity>().last("FOR UPDATE"));}
    void reserve(IdentityEmailBudgetEntity budget,long client,long realm,String target,String source){
        long minute=clock.now().getEpochSecond()/60,day=clock.now().getEpochSecond()/86400;
        int minutes=Objects.equals(budget.getMinuteSlot(),minute)?budget.getMinuteStarts():0,days=Objects.equals(budget.getDaySlot(),day)?budget.getDayStarts():0;
        if(minutes>=settings.tenantPerMinute() || days>=settings.tenantPerDay() || budget.getOutstanding()>=settings.maximumOutstanding() || budget.getRetained()>=settings.maximumRetained())throw limited();
        cleanup();String targetKey=realm+":"+target;
        if(!charge("start-client:"+client,minute,settings.clientPerMinute(),Instant.ofEpochSecond((minute+2)*60),false)
                || !charge("start-source:"+source(budget,source),minute,settings.sourcePerMinute(),Instant.ofEpochSecond((minute+2)*60),false)
                || !charge("target-day:"+targetKey,day,settings.targetPerDay(),Instant.ofEpochSecond((day+1)*86400+3600),false)
                || !charge("target-cooldown:"+targetKey,minute,1,clock.now().plusSeconds(60),true))throw limited();
        budget.setMinuteSlot(minute);budget.setDaySlot(day);budget.setMinuteStarts(minutes+1);budget.setDayStarts(days+1);
        budget.setOutstanding(budget.getOutstanding()+1);budget.setRetained(budget.getRetained()+1);rows.updateById(budget);
    }
    boolean verification(IdentityEmailBudgetEntity budget,String handle,String address){
        long minute=clock.now().getEpochSecond()/60,day=clock.now().getEpochSecond()/86400;
        int minutes=Objects.equals(budget.getGuessMinute(),minute)?budget.getMinuteGuesses():0,days=Objects.equals(budget.getGuessDay(),day)?budget.getDayGuesses():0;
        if(minutes>=settings.guessesPerMinute() || days>=settings.guessesPerDay())return false;
        cleanup();
        // Charge tenant attempts even when a narrower scope rejects the request. Never throw a business rejection after charging.
        budget.setGuessMinute(minute);budget.setGuessDay(day);budget.setMinuteGuesses(minutes+1);budget.setDayGuesses(days+1);rows.updateById(budget);
        return charge("guess-client:"+handle,minute,settings.guessesPerMinute(),Instant.ofEpochSecond((minute+2)*60),false)
                && charge("guess-source:"+source(budget,address),minute,settings.sourceGuessesPerMinute(),Instant.ofEpochSecond((minute+2)*60),false);
    }
    boolean passwordTarget(long realm,String target,int maximum){
        long minute=clock.now().getEpochSecond()/60;
        return charge("password-target:"+realm+":"+target,minute,maximum,Instant.ofEpochSecond((minute+2)*60),false);
    }
    void terminal(IdentityEmailBudgetEntity row){if(row.getOutstanding()<1)throw new IllegalStateException("Missing email reservation");row.setOutstanding(row.getOutstanding()-1);rows.updateById(row);}
    void purged(IdentityEmailBudgetEntity row){if(row.getRetained()<1)throw new IllegalStateException("Missing email retention reservation");row.setRetained(row.getRetained()-1);rows.updateById(row);}
    int cleanup(){
        var expired=rates.selectList(new LambdaQueryWrapper<IdentityEmailRateEntity>().le(IdentityEmailRateEntity::getExpiresAt,clock.now()).last("LIMIT 100 FOR UPDATE"));
        expired.forEach(row->rates.purge(row.getId()));return expired.size();
    }
    private boolean charge(String key,long slot,int maximum,Instant expiry,boolean cooldown){
        var row=rates.selectOne(new LambdaQueryWrapper<IdentityEmailRateEntity>().eq(IdentityEmailRateEntity::getScopeKey,key).last("FOR UPDATE"));
        if(row==null){
            if(rates.selectList(new LambdaQueryWrapper<IdentityEmailRateEntity>().select(IdentityEmailRateEntity::getId).last("LIMIT 10000 FOR UPDATE")).size()>=settings.maximumRateKeys())return false;
            row=new IdentityEmailRateEntity();row.setScopeKey(key);row.setWindowSlot(slot);row.setAttempts(1);row.setExpiresAt(expiry);rates.insert(row);return true;
        }
        if(cooldown && row.getExpiresAt().isAfter(clock.now()))return false;
        int count=cooldown?0:Objects.equals(row.getWindowSlot(),slot)?row.getAttempts():0;if(count>=maximum)return false;
        row.setWindowSlot(slot);row.setAttempts(count+1);row.setExpiresAt(expiry);rates.updateById(row);return true;
    }
    private String source(IdentityEmailBudgetEntity budget,String source){
        char[] value=address(source).toCharArray();try{return secrets.fingerprint(new TenantConfigurationRef(budget.getTenantId(),"identity",budget.getId(),1,1),"email-source",value);}
        finally{Arrays.fill(value,'\0');}
    }
    static String address(String raw){
        try{
            if(raw==null || raw.length()>45)throw new IllegalArgumentException();
            if(raw.contains(":")){if(!raw.matches("[0-9a-fA-F:.]+"))throw new IllegalArgumentException();return InetAddress.getByName(raw).getHostAddress();}
            if(!raw.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}"))throw new IllegalArgumentException();
            var parts=raw.split("\\.");byte[] bytes=new byte[4];for(int i=0;i<4;i++){int value=Integer.parseInt(parts[i]);if(value>255)throw new IllegalArgumentException();bytes[i]=(byte)value;}return InetAddress.getByAddress(bytes).getHostAddress();
        }catch(Exception invalid){throw new BizException(IdentityErrorCode.INVALID_EMAIL_CHALLENGE);}
    }
    private BizException limited(){return new BizException(IdentityErrorCode.EMAIL_CHALLENGE_LIMIT);}
}
