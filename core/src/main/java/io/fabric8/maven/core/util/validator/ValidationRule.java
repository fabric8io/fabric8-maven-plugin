package io.fabric8.maven.core.util.validator;

import com.networknt.schema.ValidationMessage;

/**
 * Created by hshinde on 9/23/17.
 */
public interface ValidationRule {
    String TYPE = "type";

    boolean ignore(ValidationMessage msg);
}
