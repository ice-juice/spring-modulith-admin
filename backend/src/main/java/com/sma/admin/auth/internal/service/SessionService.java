package com.sma.admin.auth.internal.service;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.auth.*;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.iam.*;

@Service
@EnableConfigurationProperties(AuthProperties.class)
public class SessionService {
    private final SessionMapper mapper;
    private final AccountQuery accounts;
    private final RoleAccessQuery scopes;
    private final PermissionQuery permissions;
    private final NavigationQuery navigation;
    private final CaptchaService captchas;
    private final LoginThrottle throttle;
    private final AuthProperties properties;
    private final LoginEvents events;
    private final SessionIssuer issuer;
    private final SessionManager manager;
    private final SessionValidity validity;
    private final SessionActivityService activity;
    private final com.sma.admin.config.ParameterQuery settings;
    private final com.sma.admin.config.PreferenceQuery preferences;
    public SessionService(SessionMapper mapper,AccountQuery accounts,RoleAccessQuery scopes,PermissionQuery permissions,NavigationQuery navigation,
            CaptchaService captchas,LoginThrottle throttle,AuthProperties properties,LoginEvents events,SessionIssuer issuer,SessionManager manager,SessionValidity validity,com.sma.admin.config.ParameterQuery preferencesSettings,com.sma.admin.config.PreferenceQuery preferences,SessionActivityService activity) {
        this.mapper=mapper; this.accounts=accounts; this.scopes=scopes; this.permissions=permissions; this.navigation=navigation;
        this.captchas=captchas; this.throttle=throttle; this.properties=properties; this.events=events; this.issuer=issuer;
        this.manager=manager;
        this.validity=validity;
        this.activity=activity;
        this.settings=preferencesSettings;this.preferences=preferences;
    }
    public record LoginResult(String status,String token,Instant expiresAt,String challengeToken,boolean rememberAllowed) {
        public LoginResult(String token,Instant expiresAt){this("AUTHENTICATED",token,expiresAt,null,true);}
    }
    public record Resolved(SessionEntity session,SmaPrincipal principal) {}
    public record Me(Long id,String username,String name,String portal,Long departmentId,boolean superAdmin,Set<String> permissions,
            List<MenuView> menus,List<NavigationView> navigation,String locale,String timeZone,boolean locked,Instant passwordChangedAt,boolean passwordChangeSuggested,
            String tenantTimeZone,String theme,boolean tabsEnabled,boolean keepTabsAfterReload,java.util.Map<String,Boolean> features,Long avatarFileId,String skin,java.util.Map<String,String> permissionIcons) {}

    public LoginResult login(String username,String password,String portal,String captchaId,String captcha,boolean remember,String ip,String userAgent) {
        com.sma.admin.common.security.TenantEntryScope.optional().ifPresent(entry -> {
            if(!entry.portal().equals(portal)) throw new BizException(AuthErrorCode.INVALID_CREDENTIALS);
        });
        String key=throttle.key(ip,username);
        try { throttle.claimLogin(ip,portal,username); throttle.check(key); }
        catch(BizException ex) { publish(null,username,portal,"RATE_LIMITED",ip,userAgent); throw ex; }
        try { if(settings.booleanValue("login.captchaEnabled",true))captchas.verify(captchaId,captcha); }
        catch(BizException ex) { throttle.fail(key); publish(null,username,portal,"CAPTCHA_FAILURE",ip,userAgent); throw ex; }
        var account=accounts.authenticate(username,password,portal);
        if(account.isEmpty()) { throttle.fail(key); publish(null,username,portal,"PASSWORD_FAILURE",ip,userAgent); throw new BizException(AuthErrorCode.INVALID_CREDENTIALS); }
        throttle.success(key);
        AccountView user=account.get();
        return TenantScope.within(user.tenantId(),() -> issuer.begin(user,settings.booleanValue("auth.singleTerminal",properties.singleTerminal()),
                Duration.ofSeconds(settings.longValue(remember?"auth.rememberTtlSeconds":"auth.sessionTtlSeconds",
                        (remember?properties.rememberTtl():properties.sessionTtl()).toSeconds())),ip,userAgent,
                remember&&settings.booleanValue("auth.lockAfterRemember",false)));
    }
    public Optional<Resolved> resolve(String token) {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        var row=mapper.selectOne(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getTokenHash,Tokens.hash(token)));
        if(!validity.valid(row,true)) return Optional.empty();
        var account=accounts.findById(row.getUserId());
        if(account.isEmpty()||!account.get().enabled()||!account.get().portal().equals(row.getPortal())
                ||account.get().credentialVersion()!=row.getCredentialVersion()) return Optional.empty();
        var user=account.get();
        return asIdentity(user,() -> {
            Duration idle=Duration.ofSeconds(settings.longValue("auth.autoLockSeconds",properties.autoLockAfter().toSeconds()));
            var current=activity.refresh(row,idle);
            if(current==null||(current!=row&&!validity.valid(current,true)))return Optional.empty();
            var principal=new SmaPrincipal(user.id(),user.username(),user.tenantId(),user.portal(),user.departmentId(),user.superAdmin(),
                    user.superAdmin()?DataAccess.unrestricted():scopes.resolve(user.roleIds(),user.departmentId()),
                    permissions.keys(user.roleIds(),user.superAdmin()),user.timeZone());
            return Optional.of(new Resolved(current,principal));
        });
    }
    public Me me(long sessionId) {
        var principal=CurrentUser.require();
        var user=accounts.findById(principal.userId()).orElseThrow(() -> new BizException(CommonErrorCode.UNAUTHORIZED));
        var menus=permissions.menus(user.roleIds(),user.superAdmin());
        var session=mapper.selectById(sessionId);
        var preference=preferences.get(user.id(),user.locale(),user.timeZone());
        return new Me(user.id(),user.username(),user.name(),user.portal(),user.departmentId(),user.superAdmin(),principal.permissions(),menus,
                navigation.visible(menus.stream().map(MenuView::id).collect(java.util.stream.Collectors.toSet())),preference.locale(),preference.timeZone(),
                session!=null&&session.getLocked(),user.passwordChangedAt(),settings.longValue("auth.passwordNoticeDays",properties.passwordNoticeDays())>0
                        &&user.passwordChangedAt().plus(Duration.ofDays(settings.longValue("auth.passwordNoticeDays",properties.passwordNoticeDays()))).isBefore(Instant.now()),
                preference.tenantTimeZone(),preference.theme(),preference.tabsEnabled(),preference.keepTabsAfterReload(),java.util.Map.of(
                    "departmentEnabled",settings.booleanValue("system.departmentEnabled",true),"postEnabled",settings.booleanValue("system.postEnabled",true),
                    "extendedProfileEnabled",settings.booleanValue("system.extendedProfileEnabled",true)),user.avatarFileId(),preference.skin(),permissions.icons(user.roleIds(),user.superAdmin()));
    }
    public void logout(long sessionId) { manager.logout(sessionId); }
    private void publish(Long userId,String username,String portal,String status,String ip,String userAgent) {
        events.record(userId,username,portal,status,ip,userAgent);
    }
    private String trimAgent(String value) { return value==null?null:value.substring(0,Math.min(value.length(),512)); }
    private <T> T asIdentity(AccountView user,Supplier<T> action) {
        var previous=SecurityContextHolder.getContext();
        var context=SecurityContextHolder.createEmptyContext();
        var identity=new SmaPrincipal(user.id(),user.username(),user.tenantId(),user.portal(),user.departmentId(),user.superAdmin(),DataAccess.unrestricted(),Set.of(),user.timeZone());
        context.setAuthentication(new UsernamePasswordAuthenticationToken(identity,null,List.of()));
        SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
}
