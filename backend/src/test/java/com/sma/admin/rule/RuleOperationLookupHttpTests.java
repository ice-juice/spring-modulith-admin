package com.sma.admin.rule;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.service.RuleService;
import com.sma.admin.rule.internal.service.RuleBatchService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class RuleOperationLookupHttpTests {
    @Autowired RuleService rules;
    @Autowired RuleBatchService batches;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    private static final Set<String> RIGHTS=Set.of("rule:template:publish","rule:batch:view","rule:batch:migrate");

    @Test void publicationLookupIsPureAndRestrictedToOriginalActorAndCurrentAssetScope() {
        var owner=actor(79831,79831,"admin",false,DataAccess.own(),RIGHTS);
        Users.as(owner,()->{
            var schema=new RuleSchema(Map.of(),false);
            var output=RuleSchema.Field.required(RuleSchema.Type.DECIMAL);
            var document=new RuleTemplateDocument(1,schema,schema,output,RuleDefinition.expression("1"),Map.of(),
                    List.of(new RuleTemplateDocument.Example("one",Map.of(),Map.of(),BigDecimal.ONE)));
            var scene=rules.createScene(new Scene("lookup_scene","Lookup scene",schema,output,schema,List.of(RuleDefinition.Kind.EXPRESSION)));
            var template=rules.createTemplate(new Template("lookup_template","Lookup template",scene.id(),document));
            var release=rules.publishTemplate(template.id(),new Publish(template.revision(),"publication_lookup_original"));
            var before=rules.get(template.id(),"TEMPLATE");
            String path="/api/rule/templates/"+template.id()+"/publications/publication_lookup_original";
            var result=read(owner,path); assertQuery(result);
            assertThat(tree(result).at("/data/operationKey").asText()).isEqualTo("publication_lookup_original");
            assertThat(tree(result).at("/data/release/id").asText()).isEqualTo(Long.toString(release.id()));
            read(owner,path); assertThat(rules.get(template.id(),"TEMPLATE")).isEqualTo(before);
            assertThat(tree(read(owner,path+"_missing")).at("/code").asInt()).isNotZero();
            for(var denied:List.of(actor(79832,79831,"admin",true,DataAccess.unrestricted(),RIGHTS),
                    actor(79831,79832,"admin",false,DataAccess.unrestricted(),RIGHTS),
                    actor(79831,79831,"other",false,DataAccess.unrestricted(),RIGHTS),
                    actor(79831,79831,"admin",false,DataAccess.none(),RIGHTS),
                    actor(79831,79831,"admin",false,DataAccess.own(),Set.of("rule:template:view")))) {
                var response=read(denied,path); assertThat(tree(response).at("/code").asInt()).isNotZero();
                assertThat(tree(response).at("/data/release/id").isMissingNode()).isTrue();
            }
            return null;
        });
    }

    @Test void batchLookupUsesOriginalKeyAndDoesNotRestartFailedOrMissingWork() {
        var owner=actor(79833,79833,"admin",false,DataAccess.own(),RIGHTS);
        Users.as(owner,()->{
            var report=batches.migrate(new BatchMigration("batch_lookup_original",List.of(
                    new MigrationItem(987654321L,new Migration(1,123,Map.of(),List.of(Map.of()))))));
            assertThat(report.state()).isEqualTo("COMPLETED_WITH_ERRORS");
            String path="/api/rule/migration-batches/operations/batch_lookup_original";
            var result=read(owner,path); assertQuery(result);
            assertThat(tree(result).at("/data/operationKey").asText()).isEqualTo("batch_lookup_original");
            assertThat(tree(result).at("/data/report/id").asText()).isEqualTo(Long.toString(report.id()));
            assertThat(tree(result).at("/data/report/state").asText()).isEqualTo("COMPLETED_WITH_ERRORS");
            read(owner,path); assertThat(batches.get(report.id())).isEqualTo(report);
            assertThat(tree(read(owner,path+"_missing")).at("/code").asInt()).isNotZero();
            for(var denied:List.of(actor(79834,79833,"admin",true,DataAccess.unrestricted(),RIGHTS),
                    actor(79833,79834,"admin",false,DataAccess.unrestricted(),RIGHTS),
                    actor(79833,79833,"other",false,DataAccess.unrestricted(),RIGHTS),
                    actor(79833,79833,"admin",false,DataAccess.none(),RIGHTS),
                    actor(79833,79833,"admin",false,DataAccess.own(),Set.of("rule:batch:view")),
                    actor(79833,79833,"admin",false,DataAccess.own(),Set.of("rule:batch:migrate")))) {
                var response=read(denied,path); assertThat(tree(response).at("/code").asInt()).isNotZero();
                assertThat(tree(response).at("/data/report/id").isMissingNode()).isTrue();
            }
            return null;
        });
    }
    private SmaPrincipal actor(long id,long tenant,String portal,boolean admin,DataAccess access,Set<String> rights){return new SmaPrincipal(id,"lookup-actor",tenant,portal,null,admin,access,rights,"UTC");}
    private MvcResult read(SmaPrincipal actor,String path){return Users.as(actor,()->{try{return mvc.perform(get(path).with(Users.request(actor))).andReturn();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private tools.jackson.databind.JsonNode tree(MvcResult result){try{return json.readTree(result.getResponse().getContentAsString());}catch(Exception failure){throw new IllegalStateException(failure);}}
    private void assertQuery(MvcResult result){assertThat(result.getResponse().getStatus()).isEqualTo(200);assertThat(tree(result).at("/code").asInt()).isZero();assertThat(result.getResponse().getHeader("X-Request-Policy")).isEqualTo("QUERY");assertThat(result.getResponse().getHeader("X-Operation-Id")).isNull();assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");}
}
