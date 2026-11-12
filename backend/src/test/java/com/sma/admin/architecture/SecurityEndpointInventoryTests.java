package com.sma.admin.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.request.*;

/** Compiled method/path/access contracts. New or moved exceptions require an explicit review. */
class SecurityEndpointInventoryTests {
    record NativePolicy(String handler,String access,String strategy,String budget,String retry){}
    private static final Map<String,NativePolicy> NATIVE=Map.ofEntries(
        entry("POST /app/file/upload-tasks","ApplicationUploadTaskController#initiate","PermitApplicationSelf","APPLICATION_NATIVE","认证后节点16/JSON64KiB；租户任务/字节/速率预算；scene audience与业务策略","任务原生同键完整指纹；不返回可读文件"),
        entry("GET /app/file/upload-tasks/{id}","ApplicationUploadTaskController#status","PermitApplicationSelf","APPLICATION_QUERY","认证后节点16；当前tenant/owner/realm/client与scene策略","本人状态；不授予下载权"),
        entry("GET /app/file/upload-tasks/{id}/parts","ApplicationUploadTaskController#parts","PermitApplicationSelf","APPLICATION_QUERY","认证后节点16；本人任务；每页最多100片","只读续传状态；不返回存储路径"),
        entry("PUT /app/file/upload-tasks/{id}/parts/{number}","ApplicationUploadTaskController#putPart","PermitApplicationSelf","APPLICATION_NATIVE","认证后节点16；单片默认16MiB实际字节限制；I/O有界线程池；Cookie CSRF","唯一claim；先核对再恢复；未知写者不重领"),
        entry("POST /app/file/upload-tasks/{id}/complete","ApplicationUploadTaskController#complete","PermitApplicationSelf","APPLICATION_NATIVE","认证后节点16/JSON64KiB；本人/scene；I/O有界线程池","完整冻结及摘要核验；仅SCANNING不是READY"),
        entry("DELETE /app/file/upload-tasks/{id}","ApplicationUploadTaskController#abort","PermitApplicationSelf","APPLICATION_NATIVE","认证后节点16/JSON64KiB；本人任务/version/scene","原生ABORTING；未确认清理不返还配额"),
        entry("GET /api/auth/captcha","AuthController#captcha","PermitAnonymous","AUTH_NATIVE","LoginThrottle：来源30/min；可信租户隔离","新挑战；不重放验证码"),
        entry("POST /api/auth/login","AuthController#login","PermitAnonymous","AUTH_NATIVE","LoginThrottle：来源60/5min、账号10/5min；原密码/MFA预算","原生会话/挑战；不缓存凭据响应"),
        entry("POST /api/auth/login-challenges/verify","LoginChallengeController#verify","PermitAnonymous","AUTH_NATIVE","MFA来源60/15min、账号5/15min；挑战一次消费","挑战/TOTP原生防重放；不盲重试"),
        entry("GET /api/tenant/entry","BrandEntryController#entry","PermitAnonymous","PUBLIC_QUERY","已发布投影；无主体预算；边缘限流按S5部署","只读/no-store；不输出草稿或普通配置"),
        entry("GET /api/tenant/brand-images/{revisionId}/{slot}","BrandEntryController#image","PermitAnonymous","PUBLIC_QUERY","受限PNG快照；无主体预算；边缘限流按S5部署","只读；当前可信tenant/portal的已发布素材"),
        entry("POST /open/token","OpenController#token","PermitAnonymous","MACHINE_NATIVE","64KiB正文；共享MySQL预占来源60/5min、来源+AppId10/5min、平台键上限10000","原签名/nonce/凭据版本；不重放token响应"),
        entry("GET /open/me","OpenController#me","PermitMachine","MACHINE_QUERY","64KiB正文；原机器认证/签名；边缘速率按S5部署","只读；不改签名字节"),
        entry("POST /open/echo","OpenController#echo","PermitMachine","MACHINE_QUERY","64KiB正文；原机器认证/签名；边缘速率按S5部署","无业务写入；保留原字节签名"),
        entry("POST /open/revoke","OpenController#revoke","PermitMachine","MACHINE_NATIVE","64KiB正文；原机器认证/签名；边缘速率按S5部署","撤销当前token；再用旧token拒绝"),
        entry("POST /open/mail/receipts","OpenController#mailReceipt","PermitMachine","MACHINE_NATIVE","64KiB正文；强制签名/nonce及MailReceiptPolicy","mail原生provider事件幂等；不能自动发信"),
        entry("POST /open/extensions/{deployment}/{plugin}/{entry}","OpenController#extension","PermitMachine","MACHINE_NATIVE","64KiB正文；原签名/nonce与插件工作容量","插件原生Idempotency-Key/work/reconcile"),
        entry("GET /app/identity/bootstrap","IdentityLoginController#bootstrap","PermitAnonymous","APPLICATION_QUERY","ApplicationRequestFilter：节点并发默认64、精确Host/client/Origin","只读；WEB Cookie/CSRF与NATIVE分离"),
        entry("POST /app/identity/google/transactions","IdentityLoginController#begin","PermitAnonymous","APPLICATION_NATIVE","节点并发默认64/正文64KiB；IdentityLoginProperties租户/客户端/来源预算","一次登录事务；state/proof/nonce/PKCE"),
        entry("POST /app/identity/google/completions","IdentityLoginController#complete","PermitAnonymous","APPLICATION_NATIVE","节点并发默认64/正文64KiB；登录事务/提供方网络预算","VERIFYING不能重兑；token不入回执"),
        entry("POST /app/identity/sessions/refresh","IdentityLoginController#refresh","PermitAnonymous","APPLICATION_NATIVE","节点并发默认64/正文64KiB；精确客户端+CSRF或NATIVE凭据","刷新族一次轮换；重复使用撤销族"),
        entry("POST /app/identity/email/challenges","IdentityEmailLoginController#begin","PermitAnonymous","APPLICATION_NATIVE","节点64/正文64KiB；租户/客户端/来源/邮箱冷却及日额度","REGISTRATION或PASSWORD_RESET；挑战和邮件同事务；不盲重发"),
        entry("POST /app/identity/email/verifications","IdentityEmailLoginController#verify","PermitAnonymous","APPLICATION_NATIVE","节点64/正文64KiB；来源预算及挑战最多5次猜测","拒绝次数独立提交；一次性grant，不重放秘密响应"),
        entry("POST /app/identity/registrations","IdentityEmailLoginController#register","PermitAnonymous","APPLICATION_NATIVE","节点64/正文64KiB；用途/proof/client/Origin；BCrypt并发默认4","grant消费/账号/会话同事务；不缓存令牌响应"),
        entry("POST /app/identity/password/resets","IdentityEmailLoginController#reset","PermitAnonymous","APPLICATION_NATIVE","节点64/正文64KiB；用途/proof/client/Origin；BCrypt并发默认4","仅已有密码账号；grant/改密/会话撤销原子提交"),
        entry("POST /app/identity/password/sessions","IdentityEmailLoginController#login","PermitAnonymous","APPLICATION_NATIVE","节点64/正文64KiB；独立租户/来源预算；realm邮箱默认5次每分钟；BCrypt并发4","CPU在事务外；finish重验版本；不缓存令牌响应"),
        entry("GET /app/identity/sessions/current","IdentitySessionController#current","PermitApplicationSelf","APPLICATION_QUERY","节点并发默认64；精确租户/realm/client/audience","当前应用会话；不套后台人类账本"),
        entry("DELETE /app/identity/sessions/current","IdentitySessionController#logout","PermitApplicationSelf","APPLICATION_NATIVE","节点并发默认64/正文64KiB；当前会话+WEB CSRF","原生撤销及清Cookie；旧身份不可重放"),
        entry("GET /app/identity/account/methods","IdentityAccountController#methods","PermitApplicationSelf","APPLICATION_QUERY","节点64；可信tenant/client/本人会话；最多100个Google关联","仅本人联系邮箱/方式ID；无hash/外部sub/秘密"),
        entry("POST /app/identity/account/reauthentication/password","IdentityAccountController#passwordProof","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；共享密码猜测预算；BCrypt并发4；grant容量","原会话/动作/目标一次性授权；不缓存grant"),
        entry("POST /app/identity/account/reauthentication/google/transactions","IdentityAccountController#googleProofStart","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；Google租户/客户端/来源预算","固定REAUTH intent；原actor/动作/目标；独立proof Cookie"),
        entry("POST /app/identity/account/reauthentication/google/completions","IdentityAccountController#googleProofComplete","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；Google网络/nonce/PKCE与grant容量","仅已有issuer/sub；兑换不重试；完成复查原actor"),
        entry("POST /app/identity/account/google-links/transactions","IdentityAccountController#googleLinkStart","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；一次性原账号grant与Google预算","授权消费/新LINK_GOOGLE事务原子提交"),
        entry("POST /app/identity/account/google-links/completions","IdentityAccountController#googleLinkComplete","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；provider预算；活动/历史关联容量","仅原actor；绑定/撤销旧会话原子提交；不转移身份"),
        entry("POST /app/identity/account/email-bindings/challenges","IdentityAccountController#emailStart","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；原账号grant；邮箱冷却/来源/租户预算","grant消费/绑定挑战/邮件同事务；保存原actor"),
        entry("POST /app/identity/account/email-bindings/verifications","IdentityAccountController#emailVerify","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；猜测预算；最多5次；原actor检查","拒绝次数独立提交；只签发原会话绑定grant"),
        entry("POST /app/identity/account/email-bindings/completions","IdentityAccountController#emailComplete","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；原actor+邮箱grant；BCrypt并发4","凭据/授权消费/旧会话撤销原子提交；不替换已有密码"),
        entry("DELETE /app/identity/account/password","IdentityAccountController#removePassword","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；原actor/动作/目标grant","保留其他可登录方式和验证邮箱；原子撤销旧会话"),
        entry("DELETE /app/identity/account/google-links/{id}","IdentityAccountController#removeGoogle","PermitApplicationSelf","APPLICATION_NATIVE","节点64/正文64KiB；原actor/具体关联ID grant","保留其他可登录方式与历史归属；原子撤销旧会话")
    );
    private static Map.Entry<String,NativePolicy> entry(String route,String handler,String access,String strategy,String budget,String retry){
        return Map.entry(route,new NativePolicy(handler,access,strategy,budget,retry));
    }
    @Test void everyCompiledHandlerHasReviewedGovernanceAndNoStaleExceptions() throws Exception {
        EndpointBudgetCatalog.validateCompiled();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var rows = new TreeSet<String>();
        var contracts=new TreeSet<String>();
        var errors = new ArrayList<String>();
        var seenExceptions=new HashSet<String>();
        var seenBudgets=new HashSet<String>();
        int handlers = 0;
        for (String root : List.of("com.sma.admin", "com.sma.business")) {
            for (var bean : scanner.findCandidateComponents(root)) {
                if (!bean.getBeanClassName().contains(".internal.web.")) continue;
                Class<?> type = Class.forName(bean.getBeanClassName());
                var prefix = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
                var controller=AnnotatedElementUtils.findMergedAnnotation(type,GovernedApi.class);
                for (var method : type.getDeclaredMethods()) {
                    var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                    if (mapping == null) continue;
                    handlers++;
                    String handler=type.getSimpleName()+"#"+method.getName(),access=access(type,method);
                    var override=AnnotatedElementUtils.findMergedAnnotation(method,GovernedApi.class);
                    var policy=override==null?controller:override;
                    if(access.equals("MISSING"))errors.add(handler+": missing access policy");
                    if(override!=null&&controller==null)errors.add(handler+": method policy without a marked controller is not intercepted");
                    if(mapping.method().length==0)errors.add(handler+": unrestricted HTTP methods need an explicit contract");
                    for(var verb:mapping.method())for (String base : paths(prefix)) for (String suffix : paths(mapping)) {
                        String path = base + suffix;
                        String route=verb+" "+path;var nativePolicy=NATIVE.get(route);
                        String strategy,budget,retry,detail;
                        if(nativePolicy!=null){
                            seenExceptions.add(route);
                            if(!nativePolicy.handler().equals(handler)||!nativePolicy.access().equals(access))errors.add(route+": exception handler/access drift");
                            strategy=nativePolicy.strategy();budget=nativePolicy.budget();retry=nativePolicy.retry();detail="精确原生/公开策略";
                        }else if(policy!=null&&!access.equals("PermitAnonymous")&&!access.equals("PermitMachine")&&!access.startsWith("PermitApplication")){
                            if(!RequestAuthorizer.class.isAssignableFrom(type))errors.add(handler+": missing module authorizer");
                            if(!policy.group().matches("[a-z][a-z0-9.-]{1,69}"))errors.add(handler+": invalid operation group");
                            if(policy.readLimit()<1||policy.readLimit()>100000||policy.writeLimit()<1||policy.writeLimit()>100000)errors.add(handler+": invalid limits");
                            if(policy.mode()!=GovernedApi.Mode.LOCAL&&policy.reason().isBlank())errors.add(handler+": non-local strategy needs a reason");
                            boolean safe=Set.of(RequestMethod.GET,RequestMethod.HEAD,RequestMethod.OPTIONS).contains(verb);
                            boolean read=safe||policy.mode()==GovernedApi.Mode.QUERY;
                            strategy=read?"QUERY":policy.mode().name();
                            String actorBudget=!read&&policy.mode()==GovernedApi.Mode.NATIVE?"读"+policy.readLimit()+"/写"+policy.writeLimit():Integer.toString(read?policy.readLimit():policy.writeLimit());
                            budget=actorBudget+"/min 主体+接口；租户读6000/写1500每分钟；"+(safe?"GET不占提交名额":"本节点并发默认8");
                            retry=read?"不执行写回执；原对象授权":policy.mode()==GovernedApi.Mode.LOCAL?"Idempotency-Key必填；原键查最小回执；UNKNOWN不重跑":"原生结果/任务核对；不自动重发；读写隧道按服务端协议分类";
                            detail=policy.group()+"；"+(policy.ownResource()?"本人+目标作用域；":"目标作用域；")+(policy.reason().isBlank()?(read?"普通查询":"同库本地事务"):policy.reason());
                        }else{
                            errors.add(route+" "+handler+": unreviewed request governance");strategy="MISSING";budget="未定义";retry="未定义";detail="必须审查";
                        }
                        var budgetIds=EndpointBudgetCatalog.bindings(path,verb.name(),handler,policy,access);seenBudgets.addAll(budgetIds);
                        String scope=EndpointBudgetCatalog.contracts(budgetIds);
                        rows.add("| "+verb+" | `"+path+"` | "+access+" | "+strategy+" | "+escape(budget)+" | "+escape(retry)+" | `"+handler+"` | "+escape(detail)+" | "+escape(scope)+" |");
                        var permission=AnnotatedElementUtils.findMergedAnnotation(method,com.sma.admin.common.security.RequiresPermission.class);
                        if(permission==null)permission=AnnotatedElementUtils.findMergedAnnotation(type,com.sma.admin.common.security.RequiresPermission.class);
                        contracts.add(route+" | "+type.getName()+"#"+method.getName()+" | "+access+(permission==null?"":":"+permission.value())
                                +" | "+strategy+" | "+escape(budget)+" | "+escape(detail)+" | "+escape(scope));
                    }
                }
            }
        }
        var stale=new TreeSet<>(NATIVE.keySet());stale.removeAll(seenExceptions);
        assertThat(errors).as("Every API needs an effective policy or an exact reviewed native contract").isEmpty();
        assertThat(stale).as("Removed or moved native endpoints must remove/review their exceptions").isEmpty();
        assertThat(handlers).isGreaterThan(100);
        assertThat(seenBudgets).as("Every registered budget must be bound to a live compiled endpoint").containsExactlyInAnyOrderElementsOf(EndpointBudgetCatalog.ALL.stream().map(EndpointBudgetCatalog.Budget::id).toList());
        // Catalog fields, including table names, limits and transaction semantics, are reviewed along with endpoint bindings.
        for(var registered:EndpointBudgetCatalog.ALL)contracts.add("BUDGET | "+registered.id()+" | "+registered.scope()+" | "+registered.module()+" | "+registered.storage()
                +" | "+new TreeMap<>(registered.tables())+" | "+registered.key()+" | "+registered.limits()+" | "+registered.lifecycle()+" | "+registered.scale()+" | "+registered.implementation());
        Path baseline=Path.of("src/test/resources/security-endpoint-policies.txt");
        // A deliberate developer review step, never an HTTP/client-version bypass. Ordinary builds only compare.
        if(Boolean.getBoolean("sma.security.review-policy-baseline"))
            Files.writeString(baseline,"# Reviewed method/path/access/governance contracts. Update only after reviewing endpoint effects and recovery.\n"+String.join("\n",contracts)+"\n");
        assertThat(Files.isRegularFile(baseline)).as("The reviewed API policy baseline must be present").isTrue();
        var reviewed=new TreeSet<>(Files.readAllLines(baseline).stream().filter(line->!line.isBlank()&&!line.startsWith("#")).toList());
        var additions=new TreeSet<>(contracts);additions.removeAll(reviewed);
        var removals=new TreeSet<>(reviewed);removals.removeAll(contracts);
        assertThat(additions).as("New or changed endpoints need explicit effects/retry review, including inherited class policies").isEmpty();
        assertThat(removals).as("Removed or changed endpoint contracts need a compatibility review").isEmpty();
        Path output = Path.of(System.getProperty("sma.security.inventory", "target/security-endpoint-inventory.md"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, "# 安全专项接口清单\n\n由编译后的Controller有效方法/路径/访问策略生成；构建拒绝未分类入口或失效的精确例外。GET包含MVC隐式HEAD，CORS预检和框架Actuator不属于此Controller清单。\n\n"
                + "处理方法数：" + handlers + "；映射行数：" + rows.size() + "；精确原生/公开策略："+seenExceptions.size()+"。\n\n"
                + "这是策略覆盖，不是全部入口都使用通用限流/幂等的声明。公开品牌及部分机器接口的边缘速率防护按S5部署；原生入口的参数默认值和验证边界见安全运行说明。\n\n"
                + EndpointBudgetCatalog.markdown()+"\n## 编译后端点\n\n"
                + "| HTTP | 路径 | 访问策略 | 有效分类 | 频率/并发预算 | 重试/结果 | 处理方法 | 作用域/审查原因 | 预算ID:scope:模块/存储:N实例换算 |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n"
                + String.join("\n", rows) + "\n");
    }
    private static String access(Class<?> type,Method method){
        for(var source:List.of(method,type))for(var annotation:source.getAnnotations()){
            String name=annotation.annotationType().getSimpleName();
            if(name.startsWith("Permit")||name.equals("RequiresPermission"))return name;
        }
        return "MISSING";
    }
    private static String escape(String value){return value.replace("|","/").replace("\n"," ").replace("\r"," ");}
    private List<String> paths(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) return List.of("");
        return Arrays.asList(mapping.path());
    }
}
