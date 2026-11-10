# -*- coding: utf-8 -*-
"""Strip the closed-source jboltai_sdk dependent AI module from the JBolt reference run copy."""
import json, re
from pathlib import Path
from paths import JBOLT_HOME, JBOLT_RUN_HOME, RESEARCH_DOCS  # noqa: E402

RUN = JBOLT_RUN_HOME
J = RUN / "src/main/java"


def edit(rel, pairs):
    p = RUN / rel
    s = p.read_text(encoding="utf-8")
    for old, new in pairs:
        if old not in s:
            if new in s:
                continue
            raise SystemExit(f"pattern not found in {rel}: {old[:60]!r}")
        s = s.replace(old, new)
    p.write_text(s, encoding="utf-8")
    print("edited", rel)


edit("pom.xml", [
    ("""		<dependency>
			<groupId>com.jboltai</groupId>
			<artifactId>jboltai_sdk</artifactId>
			<version>${jboltai_sdk.version}</version>
		</dependency>
""", "		<!-- reference run: jboltai_sdk is not publicly available, AI module excluded -->\n"),
    ("""					<compilerArgument>-parameters</compilerArgument>
				</configuration>""", """					<compilerArgument>-parameters</compilerArgument>
					<excludes>
						<exclude>com/jboltai/**</exclude>
					</excludes>
				</configuration>"""),
])

edit("src/main/java/cn/jbolt/starter/ProjectServer.java", [
    ("import com.jboltai.sdk.config.JBoltAIConfig;\n", ""),
    ("System.out.println(JBoltAIConfig.JBOLT_AI_NAME_STR);", 'System.out.println("JBoltAI (reference run, AI module excluded)");'),
    ('System.out.println("JBoltAI SDK          : " + JBoltAIConfig.VERSION);', 'System.out.println("JBoltAI SDK          : excluded");'),
])

edit("src/main/java/cn/jbolt/common/config/ProjectConfig.java", [
    ("import com.jboltai.platform.ai.AiAppPublishRoutes;\n", ""),
    ("import com.jboltai.platform.ai.AiModuleAdminRoutes;\n", ""),
    ("me.add(new AiModuleAdminRoutes());", "// reference run: AI routes excluded"),
    ("me.add(new AiAppPublishRoutes());", "// reference run: AI publish routes excluded"),
])

edit("src/main/java/cn/jbolt/_admin/globalconfig/GlobalConfigService.java", [
    ("import com.jboltai.platform.ai.admin.rescenter.JBoltAiConfigService;\n", ""),
    ("""    @Inject
    JBoltAiConfigService aiConfigService;
""", ""),
    ("""       if(JBoltAiConfigService.AI_CONFIG_TYPE_KEY.equals(globalConfig.getTypeKey())){
           aiConfigService.processAiConfigAfterUpdateConfigExtends(globalConfig);
       }
""", "       // reference run: AI config hook excluded\n"),
])

edit("src/main/java/cn/jbolt/_admin/websocket/extend/JBoltWebSocketExtendCommandHandler.java", [
    ("import com.jboltai.platform.ai.admin.aiapp.AiAppService;\n", ""),
    ("import com.jboltai.platform.ai.admin.aifunction.AiFunctionService;\n", ""),
    ("Aop.get(AiFunctionService.class).callFunction(inMsg.getData().toString(),session.getToken());", "// reference run: AI function call excluded"),
    ("Aop.get(AiAppService.class).aiAppChat(inMsg.getData().toString(),session.getToken(),command);", "// reference run: AI app chat excluded"),
])

edit("src/main/java/cn/jbolt/api/common/controller/JBoltWebsocketGuestTokenApiV2Controller.java", [
    ("import com.jboltai.platform.ai.app.JBoltWebsocketGuestUseHeaderInterceptor;\n", ""),
    ("    @Before(JBoltWebsocketGuestUseHeaderInterceptor.class)\n", ""),
])

edit("src/main/java/cn/jbolt/extend/config/ExtendProjectConfig.java", [
    ("import com.jboltai.platform.ai.admin.rescenter.JBoltAiConfigService;\n", ""),
    ("import com.jboltai.platform.ai.config.JBoltAIResourceConfig;\n", ""),
    ("""		JBoltAiConfigService aiConfigService = Aop.get(JBoltAiConfigService.class);
		aiConfigService.checkAndInitAiModule();
		//数据库里配置的LLM Embedding、VDB等资源 执行资源注册初始化
		JBoltAIResourceConfig.init();
""", "		// reference run: AI module init excluded\n"),
])

for rel in ("src/main/resources/dbconfig/mysql/config.properties", "src/main/resources/dbconfig/mysql/config-pro.properties"):
    edit(rel, [(",com.jboltai.platform.ai.common.model", "")])

pf = RUN / "src/main/resources/permission_init.json"
perms = json.loads(pf.read_text(encoding="utf-8"))
kept = [p for p in perms if p.get("permissionKey") != "jbolt_ai_dev"]
pf.write_text(json.dumps(kept, ensure_ascii=False, indent="\t"), encoding="utf-8")
print("permission_init.json top-level", len(perms), "->", len(kept))

left = [str(p.relative_to(J)) for p in J.rglob("*.java") if not str(p.relative_to(J)).startswith("com") and re.search(r"import\s+(static\s+)?com\.jboltai", p.read_text(encoding="utf-8", errors="replace"))]
print("remaining cn.* files importing com.jboltai:", left)

