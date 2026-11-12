package com.sma.admin.architecture;

import java.util.*;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.request.GovernedApi;

/** Reviewed inventory metadata, not a runtime limiter. Every binding enters the compiled endpoint policy baseline. */
final class EndpointBudgetCatalog {
    enum Scope { shared, node }
    record Budget(String id,Scope scope,String module,String storage,String implementation,Map<String,String> tables,
                  String key,String limits,String lifecycle,Long nodeDefault) {
        Budget {
            if(id==null || !id.matches("[a-z]+(?:[.-][a-z]+)+") || scope==null || module==null || storage==null || storage.isBlank()
                    || implementation==null || key==null || key.isBlank() || limits==null || limits.isBlank() || lifecycle==null || lifecycle.isBlank()
                    || scope==Scope.shared && (tables.isEmpty() || nodeDefault!=null || !storage.equals("platform-mysql"))
                    || scope==Scope.node && (!tables.isEmpty() || nodeDefault==null || nodeDefault<1 || !storage.equals("process-memory")))
                throw new IllegalArgumentException("Incomplete budget scope registration");
            tables=Map.copyOf(tables);
        }
        long aggregate(long perScope,int nodes){if(perScope<1 || nodes<1)throw new IllegalArgumentException("Positive deployment capacity required");return scope==Scope.shared?perScope:Math.multiplyExact(perScope,nodes);}
        String scale(){return scope==Scope.shared?"同一资源/键/窗口：L（不乘N）":"同配置：N × "+nodeDefault+"；异配置：Σ Lᵢ";}
        String contract(){return id+":"+scope+":"+module+"/"+storage+":"+scale();}
    }
    private static String type(String relative){return "com.sma.admin."+relative;}
    private static Budget shared(String id,String module,String implementation,Map<String,String> tables,String key,String limits,String lifecycle){
        return new Budget(id,Scope.shared,module,"platform-mysql",type(implementation),tables,key,limits,lifecycle,null);
    }
    private static Budget node(String id,String module,String implementation,long limit,String key,String limits){
        return new Budget(id,Scope.node,module,"process-memory",type(implementation),Map.of(),key,limits,"进程内名额；不持久；以实际工作结束归还",limit);
    }
    static final List<Budget> ALL=List.of(
        shared("auth.security","auth","auth.internal.service.SecurityRateLimit",Map.of(type("auth.internal.persistence.SecurityThrottleEntity"),"auth_throttle"),
                "tenant + scope + subjectHash","验证码来源30/min；登录来源60/5min、账号10/5min；其余按认证动作","REQUIRES_NEW预占；后续认证成功/失败不退还"),
        shared("openapi.login","openapi","openapi.internal.service.OpenLoginThrottle",Map.of(type("openapi.internal.persistence.LoginBudgetEntity"),"openapi_login_budget",type("openapi.internal.persistence.LoginBudgetGuardEntity"),"openapi_login_budget_guard"),
                "登录前全平台SOURCE/IP、PAIR/IP+AppId；非租户表例外","来源60/5min；来源+AppId10/5min；最多10000键","独立事务分别预占；拒绝PAIR不退还SOURCE"),
        shared("identity.login","identity","identity.internal.service.IdentityLoginBudget",Map.of(type("identity.internal.persistence.IdentityLoginBudgetEntity"),"identity_login_budget",type("identity.internal.persistence.IdentityLoginRateEntity"),"identity_login_rate"),
                "tenant；client；source","默认租户200/min、5000/day；客户端20/min；来源10/min；活动1000/保留10000","MANDATORY参与登录工作事务；终态回收活动数，频率不随后续业务回滚退还"),
        shared("identity.email","identity","identity.internal.service.IdentityEmailBudget",Map.of(type("identity.internal.persistence.IdentityEmailBudgetEntity"),"identity_email_budget",type("identity.internal.persistence.IdentityEmailRateEntity"),"identity_email_rate"),
                "tenant；client；source；realm+target；用途","默认挑战租户100/min、2000/day；来源5/min；目标10/day；猜测租户500/min、来源30/min","MANDATORY参与挑战工作事务；验证拒绝计数由所属工作独立提交；准入事务失败可回滚"),
        shared("file.tasks","file","file.internal.service.UploadTaskBudget",Map.of(type("file.internal.persistence.TaskBudgetEntity"),"file_task_budget"),
                "tenant","默认30/min、1000/day、活动100、保留10000、预占100GiB；每任务3倍字节","MANDATORY参与独立任务事务；取消/到期未确认清理不能释放字节"),
        shared("common.requests","common","common.internal.service.RequestExecutionService",Map.of(type("common.internal.persistence.RequestBudgetEntity"),"common_request_budget"),
                "tenant + actor + operation；tenant总量；对象冷却","GovernedApi主体读240/写60每分钟默认；租户读6000/写1500每分钟；冷却500ms默认","独立短事务预占；业务失败不回退频率；不是幂等账本存放位置"),
        node("tenant.brand-work","tenant","tenant.internal.service.BrandWorkBudget",4,"节点内跨租户共享的品牌图片事务","固定4；仅含图片的prepare/existing；事务完成释放"),
        node("common.admission","common","common.internal.service.RequestExecutionService",8,"节点内共享提交槽位","sma.security.requests.max-concurrent=8；LOCAL/NATIVE/POST QUERY；普通GET不占"),
        node("common.browser-json","common","common.internal.security.BrowserRequestFilter",16,"节点同步API JSON读取","sma.security.browser.maximum-concurrent-json=16；只在正文读取时占用；2MiB默认"),
        node("common.anonymous-json","common","common.internal.security.BrowserRequestFilter",4,"节点匿名JSON读取子预算","sma.security.browser.maximum-concurrent-anonymous-json=4；与总JSON名额同时约束"),
        node("identity.http","identity","identity.internal.ApplicationRequestFilter",64,"节点全部/app请求","sma.identity.http.maximum-concurrent=64；同槽位覆盖认证及下游同步请求"),
        node("identity.password","identity","identity.internal.service.IdentityPasswordWork",4,"节点BCrypt CPU工作","sma.identity.password.maximum-concurrent=4；实际密码计算才占用"),
        node("file.http","file","file.internal.web.UploadTaskRequestFilter",16,"节点/api和/app上传任务入口共享","固定16；JSON64KiB；分片尺寸实际限读"),
        node("file.local-transfer","file","file.internal.service.LocalTransferStorage",8,"节点本地文件I/O线程池","sma.file.local-transfer.maximum-concurrent=8；任务每并发片另受持久任务状态限制")
    );
    static Budget byId(String id){return ALL.stream().filter(b->b.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown budget: "+id));}
    static Set<String> bindings(String path,String verb,String handler,GovernedApi governance,String access){
        var ids=new TreeSet<String>();boolean safe=Set.of("GET","HEAD","OPTIONS").contains(verb);
        if(path.startsWith("/api/") && !safe){ids.add("common.browser-json");if(access.equals("PermitAnonymous"))ids.add("common.anonymous-json");}
        if(governance!=null && !access.equals("PermitAnonymous") && !access.equals("PermitMachine") && !access.startsWith("PermitApplication")){
            ids.add("common.requests");if(!safe)ids.add("common.admission");
        }
        if(path.startsWith("/app/"))ids.add("identity.http");
        if(Set.of("AuthController#captcha","AuthController#login","AuthController#unlock","LoginChallengeController#verify",
                "AccountSecurityController#enroll","AccountSecurityController#confirm","AccountSecurityController#disable",
                "SecurityPasswordController#set","SecurityPasswordController#change","SecurityPasswordController#reset").contains(handler))ids.add("auth.security");
        if(handler.equals("OpenController#token"))ids.add("openapi.login");
        if(Set.of("IdentityLoginController#begin","IdentityLoginController#complete","IdentityAccountController#googleProofStart","IdentityAccountController#googleProofComplete",
                "IdentityAccountController#googleLinkStart","IdentityAccountController#googleLinkComplete").contains(handler))ids.add("identity.login");
        if(handler.startsWith("IdentityEmailLoginController#") || Set.of("IdentityAccountController#passwordProof","IdentityAccountController#emailStart",
                "IdentityAccountController#emailVerify","IdentityAccountController#emailComplete").contains(handler))ids.add("identity.email");
        if(Set.of("IdentityEmailLoginController#register","IdentityEmailLoginController#reset","IdentityEmailLoginController#login",
                "IdentityAccountController#passwordProof","IdentityAccountController#emailComplete").contains(handler))ids.add("identity.password");
        if(handler.startsWith("UploadTaskController#") || handler.startsWith("ApplicationUploadTaskController#")){
            ids.add("file.tasks");ids.add("file.http");if(handler.endsWith("#putPart") || handler.endsWith("#complete"))ids.add("file.local-transfer");
        }
        if(Set.of("BrandController#save","BrandController#publish","BrandController#rollback","BrandController#preview").contains(handler))ids.add("tenant.brand-work");
        return Collections.unmodifiableSet(ids);
    }
    static String contracts(Set<String> ids){return ids.isEmpty()?"none-reviewed:无本目录预算；非无限容量承诺":String.join("; ",ids.stream().sorted().map(id->byId(id).contract()).toList());}
    static String markdown(){
        var text=new StringBuilder("## 预算 scope 与物理归属（X3）\n\nshared为同一数据库资源和同一键上的累计额度；node为每进程门禁。N个同配置实例的node容量乘N，shared不乘N。下表描述受审预算层，不把下游每个业务/任务容量穷举为HTTP限流；端点引用可能仅在相应分支实际消费。none-reviewed表示本目录无预算，不能解释为无限制或已部署WAF。\n\n");
        text.append("| ID | scope | 所属模块 | 存储位置 | 隔离键/作用域 | 默认限制/条件 | 事务/生命周期 | N实例换算 | 实现 |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        ALL.stream().sorted(Comparator.comparing(Budget::id)).forEach(b->text.append("| ").append(b.id()).append(" | ").append(b.scope()).append(" | ").append(b.module()).append(" | ")
                .append(b.storage()).append(b.tables().isEmpty()?"":":"+String.join(",",new TreeSet<>(b.tables().values()))).append(" | ").append(b.key()).append(" | ").append(b.limits())
                .append(" | ").append(b.lifecycle()).append(" | ").append(b.scale()).append(" | `").append(b.implementation()).append("` |\n"));
        return text.toString();
    }
    static void validateCompiled()throws Exception{
        var ids=new HashSet<String>();
        for(var b:ALL){
            if(!ids.add(b.id()) || !b.implementation().startsWith(type(b.module()+".")))throw new IllegalStateException("Budget ownership conflict");
            Class.forName(b.implementation());
            for(var table:b.tables().entrySet()){
                var annotation=Class.forName(table.getKey()).getAnnotation(TableName.class);
                if(annotation==null || !annotation.value().equals(table.getValue()) || !table.getValue().startsWith(b.module()+"_"))throw new IllegalStateException("Budget table drift: "+b.id());
            }
        }
    }
}
