package com.arify.application.internals.validators;

import java.util.ArrayList;
import java.util.List;

public class ArifyValidator {
    public final List<ArifyValidationRuleResponse> brokenRules;

    public ArifyValidator() {
        this.brokenRules = new ArrayList<>();
    }

    public FieldValidator field(String fieldName, Object value) {
        return new FieldValidator(fieldName, value);
    }

    public void addRules(List<ArifyValidationRuleResponse> rules) {
        brokenRules.addAll(rules);
    }

    public List<ArifyValidationRuleResponse> validate() {
        return List.copyOf(brokenRules);
    }
}
