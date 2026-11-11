package com.sma.admin.identity.internal.service;

import java.net.InetAddress;
import java.time.ZoneOffset;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.identity.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** All login mutations first lock this tenant's budget, keeping reservations and rate keys bounded. */
@Service @Transactional(propagation=Propagation.MANDATORY)
public class IdentityLoginBudget {
    private final IdentityLoginBudgetMapper budgets;private final IdentityLoginRateMapper rates;
    private final IdentityLoginProperties settings;private final IdentityClock clock;private final TenantSecretCodec secrets;
    public IdentityLoginBudget(IdentityLoginBudgetMapper budgets,IdentityLoginRateMapper rates,IdentityLoginProperties settings,IdentityClock clock,TenantSecretCodec secrets){
        this.budgets=budgets;this.rates=rates;this.settings=settings;this.clock=clock;this.secrets=secrets;
    }
    IdentityLoginBudgetEntity lock(){
        IdentityAuthority.worker();long tenant=ActorContext.tenantId();budgets.ensure(IdWorker.getId(),tenant,clock.now());
        var row=budgets.selectOne(new LambdaQueryWrapper<IdentityLoginBudgetEntity>().last("FOR UPDATE"));
        if(row==null)throw new IllegalStateException("Missing identity login budget");return row;
    }
    IdentityLoginBudgetEntity existing(){IdentityAuthority.worker();return budgets.selectOne(new LambdaQueryWrapper<IdentityLoginBudgetEntity>().last("FOR UPDATE"));}
    void reserve(IdentityLoginBudgetEntity budget,long client,String source){
        long minute=clock.now().getEpochSecond()/60;var day=clock.now().atOffset(ZoneOffset.UTC).toLocalDate();
        int minuteStarts=Objects.equals(budget.getMinuteSlot(),minute)?budget.getMinuteStarts():0;
        int dayStarts=Objects.equals(budget.getDaySlot(),day)?budget.getDayStarts():0;
        if(minuteStarts>=settings.tenantPerMinute() || dayStarts>=settings.tenantPerDay() || budget.getOutstanding()>=settings.maximumOutstanding()
                || budget.getRetained()>=settings.maximumRetained())throw limited();
        var expired=rates.selectList(new LambdaQueryWrapper<IdentityLoginRateEntity>().le(IdentityLoginRateEntity::getExpiresAt,clock.now()).last("LIMIT 100 FOR UPDATE"));
        expired.forEach(row->rates.purge(row.getId()));
        char[] address=address(source).toCharArray();String key;
        try{key=secrets.fingerprint(new TenantConfigurationRef(budget.getTenantId(),"identity",budget.getId(),1,1),"google-login-source",address);}
        finally{Arrays.fill(address,'\0');}
        charge("client:"+client,settings.clientPerMinute(),minute);charge("source:"+key,settings.sourcePerMinute(),minute);
        budget.setMinuteSlot(minute);budget.setMinuteStarts(minuteStarts+1);budget.setDaySlot(day);budget.setDayStarts(dayStarts+1);
        budget.setOutstanding(budget.getOutstanding()+1);budget.setRetained(budget.getRetained()+1);budgets.updateById(budget);
    }
    void terminal(IdentityLoginBudgetEntity budget){
        if(budget.getOutstanding()<1)throw new IllegalStateException("Missing login reservation");
        budget.setOutstanding(budget.getOutstanding()-1);budgets.updateById(budget);
    }
    void purged(IdentityLoginBudgetEntity budget){
        if(budget.getRetained()<1)throw new IllegalStateException("Missing retained login reservation");
        budget.setRetained(budget.getRetained()-1);budgets.updateById(budget);
    }
    private void charge(String key,int maximum,long minute){
        var row=rates.selectOne(new LambdaQueryWrapper<IdentityLoginRateEntity>().eq(IdentityLoginRateEntity::getScopeKey,key).last("FOR UPDATE"));
        if(row==null){
            int total=rates.selectList(new LambdaQueryWrapper<IdentityLoginRateEntity>().select(IdentityLoginRateEntity::getId).last("LIMIT 10000 FOR UPDATE")).size();
            if(total>=settings.maximumRateKeys())throw limited();
            row=new IdentityLoginRateEntity();row.setScopeKey(key);row.setMinuteSlot(minute);row.setStarts(1);row.setExpiresAt(clock.now().plusSeconds(120));rates.insert(row);
        }else{
            int count=Objects.equals(row.getMinuteSlot(),minute)?row.getStarts():0;if(count>=maximum)throw limited();
            row.setMinuteSlot(minute);row.setStarts(count+1);row.setExpiresAt(clock.now().plusSeconds(120));rates.updateById(row);
        }
    }
    private String address(String raw){
        try{
            if(raw==null || raw.length()>45)throw new IllegalArgumentException();
            if(raw.contains(":")){if(!raw.matches("[0-9a-fA-F:.]+"))throw new IllegalArgumentException();return InetAddress.getByName(raw).getHostAddress();}
            if(!raw.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}"))throw new IllegalArgumentException();
            var parts=raw.split("\\.");byte[] bytes=new byte[4];for(int i=0;i<4;i++){int value=Integer.parseInt(parts[i]);if(value>255)throw new IllegalArgumentException();bytes[i]=(byte)value;}
            return InetAddress.getByAddress(bytes).getHostAddress();
        }catch(Exception malformed){throw new BizException(IdentityErrorCode.INVALID_GOOGLE_PROOF);}
    }
    private BizException limited(){return new BizException(IdentityErrorCode.LOGIN_LIMIT);}
}
