package com.sma.admin.rule.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.core.NodeComponent;
import com.yomahub.liteflow.flow.FlowBus;
import com.yomahub.liteflow.property.LiteflowConfig;

class LiteFlowCompatibilityTests {
    static class Invocation {
        final ExpressionEngine engine = new ExpressionEngine();
        final Map<String, Object> params;
        ExpressionEngine.Evaluation result;
        Invocation(String ratio) { params = Map.of("ratio", new BigDecimal(ratio)); }
    }
    public static class ExpressionNode extends NodeComponent {
        @Override public void process() {
            Invocation request = getRequestData();
            request.result = request.engine.evaluate("input.amount * params.ratio", Map.of("amount", new BigDecimal("100")), request.params, Map.of());
        }
    }
    @Test void javaNodeUsesQLExpress4AndIndependentVersionedChainsRemainAvailable() {
        var config = new LiteflowConfig(); config.setParseMode(com.yomahub.liteflow.enums.ParseModeEnum.PARSE_ALL_ON_FIRST_EXEC); config.setPrintExecutionLog(false);
        var executor = new FlowExecutor(config);
        try {
            FlowBus.addManagedNode("ruleCompatibilityExpression", new ExpressionNode());
            LiteFlowChainELBuilder.createChain().setChainId("ruleCompatibilityV1").setEL("THEN(ruleCompatibilityExpression)").build();
            LiteFlowChainELBuilder.createChain().setChainId("ruleCompatibilityV2").setEL("THEN(ruleCompatibilityExpression)").build();
            var first = new Invocation("0.9"); var second = new Invocation("0.5");
            assertThat(executor.execute2Resp("ruleCompatibilityV1", first).isSuccess()).isTrue();
            assertThat(executor.execute2Resp("ruleCompatibilityV2", second).isSuccess()).isTrue();
            assertThat(first.result.value()).isEqualTo(new BigDecimal("90.0"));
            assertThat(second.result.value()).isEqualTo(new BigDecimal("50.0"));
            assertThat(FlowBus.containChain("ruleCompatibilityV1")).isTrue();
        } finally {
            FlowBus.removeChain("ruleCompatibilityV1", "ruleCompatibilityV2");
            FlowBus.removeNode("ruleCompatibilityExpression");
        }
    }
}
