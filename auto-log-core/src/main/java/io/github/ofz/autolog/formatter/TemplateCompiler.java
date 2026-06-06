package io.github.ofz.autolog.formatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles log message templates into an immutable list of
 * {@link TemplateSegment}s and caches the result.
 *
 * <p>Template syntax:
 * <ul>
 *   <li>{@code {class}}, {@code {method}}, … — framework placeholders (fast switch lookup)</li>
 *   <li>{@code #{#expression}} — SpEL expression (pre-compiled at cache time,
 *       evaluated at render time with access to method parameters, {@code #result},
 *       and {@code #exception})</li>
 *   <li>All other text — literal, appended directly to the output</li>
 * </ul>
 *
 * <p>Thread-safe: the cache is backed by {@link ConcurrentHashMap} and
 * {@link CompiledTemplate} is immutable.
 *
 * @author ofz
 */
final class TemplateCompiler {

    private static final Logger log = LoggerFactory.getLogger(TemplateCompiler.class);

    private final ConcurrentHashMap<String, CompiledTemplate> cache = new ConcurrentHashMap<>();
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    /**
     * Compiles the given template string, returning a cached result when available.
     */
    CompiledTemplate compile(String template) {
        return cache.computeIfAbsent(template, this::doCompile);
    }

    private CompiledTemplate doCompile(String template) {
        List<TemplateSegment> segments = new ArrayList<>();
        boolean hasSpel = false;
        int pos = 0;
        int len = template.length();

        while (pos < len) {
            int brace = template.indexOf('{', pos);
            if (brace < 0) {
                segments.add(TemplateSegment.literal(template.substring(pos)));
                break;
            }

            // SpEL detection: '{' followed immediately by '#'.
            boolean isSpel = brace + 1 < len && template.charAt(brace + 1) == '#';

            // When the two-char delimiter "#{" is used the '#' before '{' must
            // not be emitted as literal text.  Trim it from the literal prefix.
            int literalEnd = brace;
            if (isSpel && brace > 0 && template.charAt(brace - 1) == '#') {
                literalEnd = brace - 1;
            }

            if (literalEnd > pos) {
                segments.add(TemplateSegment.literal(template.substring(pos, literalEnd)));
            }

            if (isSpel) {
                // SpEL: #{ expression }
                int depth = 1;
                int endBrace = brace + 2; // skip past "{#"
                while (endBrace < len && depth > 0) {
                    char c = template.charAt(endBrace);
                    if (c == '{') depth++;
                    else if (c == '}') depth--;
                    endBrace++;
                }
                if (depth > 0) {
                    segments.add(TemplateSegment.literal(template.substring(literalEnd)));
                    break;
                }
                // Expression includes the '#' at brace+1 (SpEL variable prefix)
                String exprText = template.substring(brace + 1, endBrace - 1).trim();
                try {
                    Expression expr = spelParser.parseExpression(exprText);
                    segments.add(TemplateSegment.spel(expr));
                    hasSpel = true;
                } catch (Exception e) {
                    log.warn("Failed to parse SpEL template expression '{}': {}", exprText, e.getMessage());
                    segments.add(TemplateSegment.literal(template.substring(literalEnd, endBrace)));
                }
                pos = endBrace;
            } else {
                // Framework placeholder: {key}
                int endBrace = template.indexOf('}', brace + 1);
                if (endBrace < 0) {
                    segments.add(TemplateSegment.literal(template.substring(brace)));
                    break;
                }
                String key = template.substring(brace + 1, endBrace);
                segments.add(TemplateSegment.placeholder(key));
                pos = endBrace + 1;
            }
        }

        return new CompiledTemplate(segments, hasSpel);
    }
}
