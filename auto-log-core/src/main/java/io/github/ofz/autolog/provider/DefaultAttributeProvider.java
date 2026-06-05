package io.github.ofz.autolog.provider;

import java.util.Collections;
import java.util.Map;

/**
 * Default no-op implementation of {@link AttributeProvider} that contributes
 * no extra attributes. Registered automatically by the auto-configuration
 * unless the user provides their own {@link AttributeProvider} bean(s).
 *
 * <p>Replace by registering one or more custom {@link AttributeProvider}
 * beans — the default provider will be skipped when any custom
 * implementation is present.
 *
 * @author ofz
 */
public class DefaultAttributeProvider implements AttributeProvider {

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.emptyMap();
    }
}
