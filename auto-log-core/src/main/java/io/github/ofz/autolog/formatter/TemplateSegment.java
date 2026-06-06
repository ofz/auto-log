package io.github.ofz.autolog.formatter;

import org.springframework.expression.Expression;

import java.util.Collections;
import java.util.List;

/**
 * A pre-compiled segment of a log message template, produced by
 * {@link TemplateCompiler}. Immutable and thread-safe.
 */
final class TemplateSegment {

    enum Type {
        /** Raw text appended directly to the output. */
        LITERAL,
        /** Framework placeholder key — resolved via switch (e.g. {@code {class}}). */
        PLACEHOLDER,
        /** SpEL expression — evaluated with a pre-compiled {@link Expression}. */
        SPEL
    }

    final Type type;
    final String text;       // LITERAL: raw text; PLACEHOLDER: key name (e.g. "class")
    final Expression spelExpr; // SPEL: pre-compiled expression (null for other types)

    private TemplateSegment(Type type, String text, Expression spelExpr) {
        this.type = type;
        this.text = text;
        this.spelExpr = spelExpr;
    }

    static TemplateSegment literal(String text) {
        return new TemplateSegment(Type.LITERAL, text, null);
    }

    static TemplateSegment placeholder(String key) {
        return new TemplateSegment(Type.PLACEHOLDER, key, null);
    }

    static TemplateSegment spel(Expression expr) {
        return new TemplateSegment(Type.SPEL, null, expr);
    }

    @Override
    public String toString() {
        switch (type) {
            case LITERAL:     return "LITERAL[" + text + "]";
            case PLACEHOLDER: return "PLACEHOLDER[" + text + "]";
            case SPEL:        return "SPEL[" + spelExpr.getExpressionString() + "]";
            default:          return "UNKNOWN";
        }
    }
}

/**
 * An immutable, pre-compiled template ready for efficient rendering.
 */
final class CompiledTemplate {

    final List<TemplateSegment> segments;
    final boolean hasSpel;

    CompiledTemplate(List<TemplateSegment> segments, boolean hasSpel) {
        this.segments = Collections.unmodifiableList(segments);
        this.hasSpel = hasSpel;
    }
}
