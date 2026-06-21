package com.arify.application.internals.validators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class RuleBuilder<T, P> {

    private final String fieldName;
    private final Function<T, P> propertyExtractor;
    private final List<RuleDefinition<P>> ruleDefinitions = new ArrayList<>();

    private record RuleDefinition<P>(
            Predicate<P> predicate,
            String errorCode,
            String message) {
    }

    public RuleBuilder(String fieldName, Function<T, P> propertyExtractor) {
        this.fieldName = fieldName;
        this.propertyExtractor = propertyExtractor;
    }

    public RuleBuilder<T, P> notNull() {
        ruleDefinitions.add(new RuleDefinition<>(
                Objects::nonNull,
                "not_null",
                "Cannot be null"));
        return this;
    }

    public RuleBuilder<T, P> notEmpty() {
        ruleDefinitions.add(new RuleDefinition<>(
                value -> {
                    if (value == null) {
                        return true;
                    }
                    if (value instanceof String str) {
                        return !str.isBlank();
                    }
                    if (value instanceof Collection<?> collection) {
                        return !collection.isEmpty();
                    }
                    if (value instanceof Map<?, ?> map) {
                        return !map.isEmpty();
                    }
                    return true;
                },
                "not_empty",
                "Cannot be empty"));
        return this;
    }

    public RuleBuilder<T, P> minLength(int minLength) {
        ruleDefinitions.add(new RuleDefinition<>(
                value -> {
                    if (value == null) {
                        return true;
                    }
                    if (value instanceof String str) {
                        return str.length() >= minLength;
                    }
                    return true;
                },
                "min_length",
                "Must have at least " + minLength + " characters"));
        return this;
    }

    public RuleBuilder<T, P> maxLength(int maxLength) {
        ruleDefinitions.add(new RuleDefinition<>(
                value -> {
                    if (value == null) {
                        return true;
                    }
                    if (value instanceof String str) {
                        return str.length() <= maxLength;
                    }
                    return true;
                },
                "max_length",
                "Cannot exceed " + maxLength + " characters"));
        return this;
    }

    public RuleBuilder<T, P> length(int exactLength) {
        ruleDefinitions.add(new RuleDefinition<>(
                value -> {
                    if (value == null) {
                        return true;
                    }
                    if (value instanceof String str) {
                        return str.length() == exactLength;
                    }
                    return true;
                },
                "exact_length",
                "Must be exactly " + exactLength + " characters"));
        return this;
    }

    public RuleBuilder<T, P> isNumeric() {
        ruleDefinitions.add(new RuleDefinition<>(
                value -> {
                    if (value == null) {
                        return true;
                    }
                    if (value instanceof String str) {
                        if (str.isEmpty()) {
                            return false;
                        }
                        for (int index = 0; index < str.length(); index++) {
                            if (!Character.isDigit(str.charAt(index))) {
                                return false;
                            }
                        }
                        return true;
                    }
                    return true;
                },
                "is_numeric",
                "Must contain only numeric digits"));
        return this;
    }

    public RuleBuilder<T, P> matches(String regex) {
        Pattern pattern = Pattern.compile(regex);
        ruleDefinitions.add(new RuleDefinition<>(
                value -> {
                    if (value == null) {
                        return true;
                    }
                    if (value instanceof String str) {
                        return pattern.matcher(str).matches();
                    }
                    return true;
                },
                "regex_match",
                "Must match pattern: " + regex));
        return this;
    }

    public RuleBuilder<T, P> must(Predicate<P> predicate, String errorCode, String message) {
        ruleDefinitions.add(new RuleDefinition<>(predicate, errorCode, message));
        return this;
    }

    public RuleBuilder<T, P> withErrorCode(String customCode) {
        if (!ruleDefinitions.isEmpty()) {
            int lastIndex = ruleDefinitions.size() - 1;
            RuleDefinition<P> lastRule = ruleDefinitions.get(lastIndex);
            ruleDefinitions.set(lastIndex, new RuleDefinition<>(
                    lastRule.predicate(),
                    customCode,
                    lastRule.message()));
        }
        return this;
    }

    public RuleBuilder<T, P> withMessage(String customMessage) {
        if (!ruleDefinitions.isEmpty()) {
            int lastIndex = ruleDefinitions.size() - 1;
            RuleDefinition<P> lastRule = ruleDefinitions.get(lastIndex);
            ruleDefinitions.set(lastIndex, new RuleDefinition<>(
                    lastRule.predicate(),
                    lastRule.errorCode(),
                    customMessage));
        }
        return this;
    }

    protected void execute(T instance, List<ArifyValidationRuleResponse> errors) {
        P value = propertyExtractor.apply(instance);

        for (RuleDefinition<P> rule : ruleDefinitions) {
            if (!rule.predicate().test(value)) {
                errors.add(new ArifyValidationRuleResponse(
                        fieldName,
                        rule.errorCode(),
                        rule.message()));
            }
        }
    }
}
