package com.sma.admin.rule.internal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.api.parsecache.LoadedParseCache;
import com.alibaba.qlexpress4.aparser.InterpolationMode;
import com.alibaba.qlexpress4.aparser.ParseTree;
import com.alibaba.qlexpress4.aparser.QLParser;
import com.alibaba.qlexpress4.aparser.RuleContext;
import com.alibaba.qlexpress4.exception.QLException;
import com.alibaba.qlexpress4.runtime.trace.ExpressionTrace;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.rule.RuleProblem;
import com.sma.admin.rule.RuleTrace;

/** A small, read-only expression profile. The engine still evaluates QLExpress, not a home-grown language. */
@Component
public class ExpressionEngine {
    private static final Set<String> NODES = Set.of("ProgramContext", "BlockStatementsContext",
            "ExpressionStatementContext", "ExpressionContext", "LeftHandSideContext", "TernaryExprContext",
            "BaseExprContext", "PrimaryContext", "LeftAssoContext", "BinaryopContext", "PrefixExpressContext", "ConstExprContext",
            "GroupExprContext", "VarIdExprContext", "ListExprContext", "MapExprContext", "ListItemsContext",
            "MapEntriesContext", "MapEntryContext", "EValueContext", "IdKeyContext", "StringKeyContext",
            "QuoteStringKeyContext", "FieldAccessContext", "OptionalFieldAccessContext", "IndexExprContext",
            "FieldIdContext", "SingleIndexContext", "ArgumentListContext", "LiteralContext",
            "DoubleQuoteStringLiteralContext", "BoolenLiteralContext", "VarIdContext", "OpIdContext");
    private static final Set<String> OPERATORS = Set.of("+", "-", "*", "/", "%", "!", "&&", "||",
            "==", "!=", ">", ">=", "<", "<=", "in", "notin");
    private static final Set<String> FUNCTIONS = Set.of("min", "max", "abs", "round", "size", "contains", "intervalValue");
    private final Express4Runner runner;
    private record Key(long tenantId, String expression) {}
    private final Map<Key, LoadedParseCache> cache = new LinkedHashMap<>(32, .75f, true);

    public ExpressionEngine() {
        runner = new Express4Runner(InitOptions.builder().classSupplier(name -> {
            throw new RuleFailure("expression", "CLASS_ACCESS_DENIED");
        }).traceExpression(true).interpolationMode(InterpolationMode.DISABLE).build());
        runner.addVarArgsFunction("min", args -> binary(args, false));
        runner.addVarArgsFunction("max", args -> binary(args, true));
        runner.addVarArgsFunction("abs", args -> { count(args, 1); return number(args[0]).abs(); });
        runner.addVarArgsFunction("round", args -> {
            count(args, 2); int scale = number(args[1]).intValueExact();
            if (scale < 0 || scale > 18) throw new RuleFailure("expression", "INVALID_SCALE");
            return number(args[0]).setScale(scale, RoundingMode.HALF_UP);
        });
        runner.addVarArgsFunction("size", args -> {
            count(args, 1);
            if (args[0] instanceof List<?> list) return list.size();
            if (args[0] instanceof String text) return text.length();
            throw new RuleFailure("expression", "INVALID_FUNCTION_ARGUMENT");
        });
        runner.addVarArgsFunction("contains", args -> {
            count(args, 2);
            if (args[0] instanceof List<?> list) return list.stream().anyMatch(item -> RuleValues.equal(item, args[1]));
            if (args[0] instanceof String text && args[1] instanceof String needle) return text.contains(needle);
            throw new RuleFailure("expression", "INVALID_FUNCTION_ARGUMENT");
        });
        runner.addVarArgsFunction("intervalValue", args -> {
            count(args, 6); return RuleIntervals.lookup(args[0], args[1], args[2], args[3], args[4], args[5]);
        });
    }
    public record Evaluation(Object value, List<RuleTrace> trace) {}
    public List<String> functions() { return FUNCTIONS.stream().sorted().toList(); }
    public void validate(String expression) { compiled(expression); }
    public Set<List<String>> referencedPaths(String expression) {
        validate(expression); return runner.getOutVarAttrs(expression);
    }
    public Evaluation evaluate(String expression, Map<String, Object> input, Map<String, Object> params,
            Map<String, Object> context) {
        var plan = compiled(expression);
        try {
            var result = runner.execute(plan, new com.alibaba.qlexpress4.runtime.context.MapExpressContext(
                    Map.of("input", input, "params", params, "context", context)),
                    QLOptions.builder().precise(true).timeoutMillis(100).maxArrLength(256)
                            .cache(false).traceExpression(true).build());
            var trace = new ArrayList<RuleTrace>();
            for (var root : result.getExpressionTraces()) collect(root, trace);
            return new Evaluation(RuleValues.freeze(result.getResult(), 0), List.copyOf(trace));
        } catch (QLException ex) { throw diagnostic(ex); }
    }
    private synchronized LoadedParseCache compiled(String source) {
        if (source == null || source.isBlank() || source.length() > 4096)
            throw new RuleFailure("expression", "SOURCE_LIMIT");
        preflight(source);
        var key = new Key(CurrentUser.tenantId(), source);
        var compiled = cache.get(key);
        if (compiled != null) return compiled;
        try {
            var tree = runner.parseToSyntaxTree(source);
            if (!tree.importDeclaration().isEmpty() || tree.blockStatements() == null
                    || tree.blockStatements().blockStatement().size() != 1)
                throw new RuleFailure("expression", "SINGLE_EXPRESSION_REQUIRED");
            int[] nodes = {0}; inspect(tree, 0, nodes);
            compiled = runner.loadSerializableCache(runner.parseToSerializableCache(source));
            long tenantSize = cache.keySet().stream().filter(item -> item.tenantId() == key.tenantId()).count();
            if (tenantSize >= 64) {
                var iterator = cache.keySet().iterator();
                while (iterator.hasNext()) if (iterator.next().tenantId() == key.tenantId()) { iterator.remove(); break; }
            }
            if (cache.size() >= 256) cache.remove(cache.keySet().iterator().next());
            cache.put(key, compiled);
            return compiled;
        } catch (QLException ex) { throw diagnostic(ex); }
    }
    private static void inspect(ParseTree tree, int depth, int[] count) {
        if (++count[0] > 1024 || depth > 64) throw new RuleFailure("expression", "EXPRESSION_LIMIT");
        if (!(tree instanceof RuleContext ctx)) return;
        if (!NODES.contains(ctx.getClass().getSimpleName()))
            throw new RuleFailure(new RuleProblem("expression", "SYNTAX_NOT_ALLOWED_" + ctx.getClass().getSimpleName(), ctx.getStart().getLine(),
                    ctx.getStart().getCharPositionInLine() + 1));
        if (ctx instanceof QLParser.BinaryopContext || ctx instanceof QLParser.OpIdContext) {
            if (!OPERATORS.contains(ctx.getText())) throw new RuleFailure("expression", "OPERATOR_NOT_ALLOWED");
        }
        if (ctx instanceof QLParser.VarIdExprContext variable) {
            String name = variable.varId().getText();
            if (variable.LPAREN() != null ? !FUNCTIONS.contains(name) : !Set.of("input", "params", "context").contains(name))
                throw new RuleFailure("expression", "UNKNOWN_VARIABLE_OR_FUNCTION");
        }
        for (ParseTree child : ctx.children()) inspect(child, depth + 1, count);
    }
    private static void preflight(String source) {
        int nesting = 0, punctuation = 0; char quote = 0; boolean escaped = false;
        var numericSource = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                numericSource.append(' ');
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; numericSource.append(' '); continue; }
            numericSource.append(c);
            if ("([{+*/%!-<>=&|?:,.".indexOf(c) >= 0 && ++punctuation > 256)
                throw new RuleFailure("expression", "EXPRESSION_LIMIT");
            if ("([{".indexOf(c) >= 0 && ++nesting > 24)
                throw new RuleFailure("expression", "EXPRESSION_LIMIT");
            if (")]}".indexOf(c) >= 0) nesting--;
        }
        var numbers = java.util.regex.Pattern.compile("(?<![a-zA-Z0-9_])[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
                .matcher(numericSource);
        while (numbers.find()) RuleValues.decimal(numbers.group(), "expression");
    }
    private static RuleFailure diagnostic(QLException ex) {
        return new RuleFailure(new RuleProblem("expression", ex.getErrorCode(), ex.getLineNo(), ex.getColNo()));
    }
    private static void collect(ExpressionTrace node, List<RuleTrace> result) {
        if (result.size() >= 256) return;
        result.add(new RuleTrace("expression:" + node.getPosition(), node.getType().name(), node.isEvaluated(),
                node.getLine(), node.getCol(), node.isEvaluated() ? "EVALUATED" : "SKIPPED"));
        node.getChildren().forEach(child -> collect(child, result));
    }
    private static BigDecimal number(Object value) {
        if (!(value instanceof Number)) throw new RuleFailure("expression", "INVALID_FUNCTION_ARGUMENT");
        return RuleValues.decimal(value, "expression");
    }
    private static void count(Object[] args, int count) {
        if (args.length != count) throw new RuleFailure("expression", "FUNCTION_ARITY");
    }
    private static BigDecimal binary(Object[] args, boolean maximum) {
        count(args, 2); return maximum ? number(args[0]).max(number(args[1])) : number(args[0]).min(number(args[1]));
    }
    public synchronized int cacheSize() { return cache.size(); }
    public synchronized void clearCache() { cache.clear(); }
    public synchronized int tenantCacheSize() { return (int) cache.keySet().stream().filter(key -> key.tenantId() == CurrentUser.tenantId()).count(); }
    public synchronized int clearTenantCache() {
        int before = cache.size(); cache.keySet().removeIf(key -> key.tenantId() == CurrentUser.tenantId()); return before - cache.size();
    }
}
