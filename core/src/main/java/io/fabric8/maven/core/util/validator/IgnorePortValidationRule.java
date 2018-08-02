package io.fabric8.maven.core.util.validator;

import com.networknt.schema.ValidationMessage;

/**
 * Model to represent ignore validation rules as tuple of json tree path and constraint ruleType
 * for resource descriptor validations
 */
public class IgnorePortValidationRule implements ValidationRule {

    private final String ruleType;

    public IgnorePortValidationRule(String aRuleType) {
        this.ruleType = aRuleType;
    }

    public String getType() {
        return ruleType;
    }

    @Override
    public boolean ignore(ValidationMessage msg) {
        return msg.getType().equalsIgnoreCase(TYPE) &&
                msg.getMessage().contains(": integer found, object expected");
    }
}
