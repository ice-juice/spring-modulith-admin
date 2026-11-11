package com.sma.admin.mail.internal.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import com.sma.admin.common.BizException;
import com.sma.admin.mail.MailErrorCode;
import com.sma.admin.mail.MailTemplateContent;

@Component
public class MailTemplateRenderer {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]{0,63})}}", Pattern.UNICODE_CASE);
    private static final Safelist HTML = Safelist.none().addTags("p", "br", "strong", "em", "b", "i", "ul", "ol", "li", "a", "code", "span")
            .addAttributes("a", "href").addProtocols("a", "href", "https");
    public record Rendered(String subject, String body, boolean html) {
        @Override public String toString() { return "RenderedMail[REDACTED]"; }
    }

    public void validate(MailTemplateContent content) {
        if (content == null || content.subject() == null || content.subject().isBlank() || content.subject().length() > 240
                || content.subject().chars().anyMatch(Character::isISOControl) || content.body() == null || content.body().isBlank()
                || content.body().length() > 16000 || content.variables().size() > 32 || content.linkHosts().size() > 20) throw invalid();
        for (var entry : content.variables().entrySet()) {
            var variable = entry.getValue();
            if (!entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || variable.type() == null
                    || variable.maxLength() < 1 || variable.maxLength() > 4096) throw invalid();
        }
        for (String host : content.linkHosts()) {
            if (!host.equals(host.toLowerCase(Locale.ROOT)) || !host.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")
                    || host.contains("..")) throw invalid();
        }
        var used = tokens(content.subject()); used.addAll(tokens(content.body()));
        if (!used.equals(content.variables().keySet())) throw invalid();
        if (content.html()) {
            var document = Jsoup.parseBodyFragment(content.body());
            for (var element : document.getAllElements()) {
                for (var attribute : element.attributes()) {
                    var matcher = TOKEN.matcher(attribute.getValue());
                    if (matcher.find()) {
                        if (!element.tagName().equals("a") || !attribute.getKey().equals("href") || !matcher.matches()
                                || content.variables().get(matcher.group(1)).type() != MailTemplateContent.Type.HTTPS_URL) throw invalid();
                        // Structural validation uses a dummy HTTPS link, never the recipient's personalized data.
                        element.attr("href", "https://template.invalid/");
                    } else if (element.tagName().equals("a") && attribute.getKey().equals("href")) {
                        checkUrl(attribute.getValue(), content.linkHosts());
                    }
                }
            }
            if (!Jsoup.isValid(document.body().html(), HTML)) throw invalid();
        }
    }

    public Rendered render(MailTemplateContent content, Map<String, String> values) {
        validate(content);
        if (values == null || !content.variables().keySet().containsAll(values.keySet())) throw invalid();
        for (var entry : content.variables().entrySet()) {
            var spec = entry.getValue(); String value = values.get(entry.getKey());
            if (value == null || value.isEmpty()) { if (spec.required()) throw invalid(); else continue; }
            if (value.length() > spec.maxLength() || value.indexOf('\0') >= 0) throw invalid();
            boolean valid = switch (spec.type()) {
                case TEXT -> true;
                case INTEGER -> value.matches("-?(?:0|[1-9][0-9]{0,17})");
                case DECIMAL -> value.matches("-?(?:0|[1-9][0-9]{0,17})(?:\\.[0-9]{1,8})?");
                case BOOLEAN -> value.equals("true") || value.equals("false");
                case HTTPS_URL -> { checkUrl(value, content.linkHosts()); yield true; }
            };
            if (!valid) throw invalid();
        }
        String subject = replace(content.subject(), values, false);
        String body = replace(content.body(), values, content.html());
        if (subject.isBlank() || subject.length() > 240 || subject.chars().anyMatch(Character::isISOControl)
                || body.getBytes(StandardCharsets.UTF_8).length > 48000) throw invalid();
        if (content.html()) {
            if (!Jsoup.isValid(body, HTML)) throw invalid();
            body = Jsoup.clean(body, HTML);
        }
        return new Rendered(subject, body, content.html());
    }

    private Set<String> tokens(String source) {
        if (source.contains("${") || source.contains("#{")) throw invalid();
        Set<String> names = new HashSet<>(); var matcher = TOKEN.matcher(source);
        while (matcher.find()) names.add(matcher.group(1));
        String rest = TOKEN.matcher(source).replaceAll("");
        if (rest.contains("{{") || rest.contains("}}")) throw invalid();
        return names;
    }

    private String replace(String source, Map<String, String> values, boolean html) {
        return TOKEN.matcher(source).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                html ? escape(values.getOrDefault(match.group(1), "")) : nonNull(values.get(match.group(1)))));
    }
    private String nonNull(String value) { return value == null ? "" : value; }
    private String escape(String value) {
        return nonNull(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private void checkUrl(String value, Set<String> allowedHosts) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || value.chars().anyMatch(Character::isISOControl)) throw invalid();
        } catch (IllegalArgumentException malformed) { throw invalid(); }
    }
    private static BizException invalid() { return new BizException(MailErrorCode.INVALID_TEMPLATE); }
}
