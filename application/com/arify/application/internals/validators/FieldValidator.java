package com.arify.application.internals.validators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FieldValidator {
    public final String fieldName;
    public final Object value;
    public final List<ArifyValidationRuleResponse> brokenRules;
    public ArifyValidationRuleResponse currentRule;
    public boolean currentRuleCanBeCustomized;

    public FieldValidator(String fieldName, Object value) {
        this.fieldName = fieldName;
        this.value = value;
        this.brokenRules = new ArrayList<>();
        this.currentRule = null;
        this.currentRuleCanBeCustomized = false;
    }

    public static FieldValidator fromObject(Object obj, String fieldName) {
        try {
            Object fieldValue = obj.getClass().getDeclaredMethod(fieldName).invoke(obj);
            return new FieldValidator(fieldName, fieldValue);
        } catch (Exception exception) {
            return new FieldValidator(fieldName, null);
        }
    }

    public static FieldValidator forValue(Object value, String alias) {
        String fieldAlias = alias == null || alias.isBlank() ? "unknown_field" : alias;
        return new FieldValidator(fieldAlias, value);
    }

    public FieldValidator appendRule(String errorCode, String message) {
        currentRule = new ArifyValidationRuleResponse(fieldName, errorCode, message);
        brokenRules.add(currentRule);
        currentRuleCanBeCustomized = true;
        return this;
    }

    public FieldValidator notNull() {
        if (value == null) {
            appendRule("not_null", "No puede ser nulo.");
        } else {
            currentRuleCanBeCustomized = false;
        }
        return this;
    }

    public FieldValidator notEmpty() {
        if (value == null) {
            currentRuleCanBeCustomized = false;
            return this;
        }

        if (value instanceof String text && text.trim().isEmpty()) {
            appendRule("not_empty", "No puede estar vacío (string).");
        } else if (value instanceof Collection<?> collection && collection.isEmpty()) {
            appendRule("not_empty", "No puede estar vacío (colección).");
        } else if (value instanceof Map<?, ?> map && map.isEmpty()) {
            appendRule("not_empty", "No puede estar vacío (colección).");
        } else {
            currentRuleCanBeCustomized = false;
        }

        return this;
    }

    public FieldValidator minLength(int minLen) {
        if (value instanceof String text && text.length() < minLen) {
            appendRule("min_length", "Debe tener al menos " + minLen + " caracteres.");
        } else {
            currentRuleCanBeCustomized = false;
        }
        return this;
    }

    public FieldValidator maxLength(int maxLen) {
        if (value instanceof String text && text.length() > maxLen) {
            appendRule("max_length", "No puede tener más de " + maxLen + " caracteres.");
        } else {
            currentRuleCanBeCustomized = false;
        }
        return this;
    }

    public FieldValidator isNumeric() {
        if (!(value instanceof String text) || text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            appendRule("is_numeric", "Debe ser una cadena que contenga solo números.");
        } else {
            currentRuleCanBeCustomized = false;
        }
        return this;
    }

    public FieldValidator withMessage(String customMessage) {
        if (currentRule != null && currentRuleCanBeCustomized) {
            replaceCurrentRule(new ArifyValidationRuleResponse(
                    currentRule.fieldName(),
                    currentRule.errorCode(),
                    customMessage));
        }
        return this;
    }

    public FieldValidator withCode(String customCode) {
        if (currentRule != null && currentRuleCanBeCustomized) {
            replaceCurrentRule(new ArifyValidationRuleResponse(
                    currentRule.fieldName(),
                    customCode,
                    currentRule.message()));
        }
        return this;
    }

    public FieldValidator when(boolean condition) {
        if (!condition && currentRule != null) {
            brokenRules.remove(currentRule);
            currentRule = null;
        }
        return this;
    }

    public List<ArifyValidationRuleResponse> validate() {
        return List.copyOf(brokenRules);
    }

    public void replaceCurrentRule(ArifyValidationRuleResponse newRule) {
        int index = brokenRules.indexOf(currentRule);
        if (index >= 0) {
            brokenRules.set(index, newRule);
        }
        currentRule = newRule;
    }
}
