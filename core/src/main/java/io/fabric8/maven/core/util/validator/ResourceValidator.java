/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.core.util.validator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import javax.validation.metadata.ConstraintDescriptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import io.fabric8.maven.core.util.ResourceClassifier;
import io.fabric8.maven.docker.util.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Validates Kubernetes/OpenShift resource descriptors using JSON schema validation method.
 * For Openshift it adds some some exceptions from JSON schema constraints and ignores some validation errors.
 */

public class ResourceValidator {

    public static final String SCHEMA_JSON = "/schema/kube-validation-schema.json";
    private Logger log;
    private File resources[];
    private ResourceClassifier target = ResourceClassifier.KUBERNETES;
    private List<ValidationRule> ignoreValidationRules = new ArrayList<>();

    /**
     * @param inputFile File/Directory path of resource descriptors
     */
    public ResourceValidator(File inputFile) {
        if(inputFile.isDirectory()) {
            resources = inputFile.listFiles();
        } else {
            resources = new File[]{inputFile};
        }
    }

    /**
     * @param inputFile File/Directory path of resource descriptors
     * @param target  Target platform e.g OpenShift, Kubernetes
     * @param log Logger for logging messages on standard output devices
     */
    public ResourceValidator(File inputFile, ResourceClassifier target, Logger log) {
        this(inputFile);
        this.target = target;
        this.log = log;
        setupIgnoreRules(this.target);
    }

    /*
     * Add exception rules to ignore validation constraint from JSON schema for OpenShift/Kubernetes resources. Some fields in JSON schema which are marked as required
     * but in reality it's not required to provide values for those fields while creating the resources.
     * e.g. In DeploymentConfig(https://docs.openshift.com/container-platform/3.6/rest_api/openshift_v1.html#v1-deploymentconfig) model 'status' field is marked as
     * required.
     */
    private void setupIgnoreRules(ResourceClassifier target) {
        ignoreValidationRules.add(new IgnorePortValidationRule(IgnorePortValidationRule.TYPE));
        ignoreValidationRules.add(new IgnoreResourceMemoryLimitRule(IgnoreResourceMemoryLimitRule.TYPE));
    }

    /**
     * Validates the resource descriptors as per JSON schema. If any resource is invalid it throws @{@link ConstraintViolationException} with
     * all violated constraints
     *
     * @return number of resources processed
     * @throws ConstraintViolationException
     * @throws IOException
     */
    public int validate() throws ConstraintViolationException, IOException {
        for(File resource: resources) {
            if (resource.isFile() && resource.exists()) {
                try {
                    log.info("validating %s resource", resource.toString());
                    JsonNode inputSpecNode = geFileContent(resource);
                    String kind = inputSpecNode.get("kind").toString();
                    JsonSchema schema = getJsonSchema(prepareSchemaUrl(SCHEMA_JSON), kind);
                    Set<ValidationMessage> errors = schema.validate(inputSpecNode);
                    processErrors(errors, resource);
                } catch (JSONException e) {
                    throw new ConstraintViolationException(e.getMessage(), new HashSet<ConstraintViolationImpl>());
                } catch (URISyntaxException e) {
                    throw new IOException(e);
                }
            }
        }

        return resources.length;
    }

    private void processErrors(Set<ValidationMessage> errors, File resource) {
        Set<ConstraintViolationImpl> constraintViolations = new HashSet<>();
        for (ValidationMessage errorMsg: errors) {
            if(!ignoreError(errorMsg))
                constraintViolations.add(new ConstraintViolationImpl(errorMsg));
        }

        if(constraintViolations.size() > 0) {
            throw new ConstraintViolationException(getErrorMessage(resource, constraintViolations), constraintViolations);
        }
    }

    private boolean ignoreError(ValidationMessage errorMsg) {
        for (ValidationRule rule : ignoreValidationRules) {
            if(rule.ignore(errorMsg)) {
                return  true;
            }
        }

        return false;
    }

    private String getErrorMessage(File resource, Set<ConstraintViolationImpl> violations) {
        StringBuilder validationError = new StringBuilder();
        validationError.append("Invalid Resource : ");
        validationError.append(resource.toString());

        for (ConstraintViolationImpl violation: violations) {
            validationError.append("\n");
            validationError.append(violation.toString());
        }

        return  validationError.toString();
    }

    private JsonSchema getJsonSchema(URI schemaUrl, String kind) throws IOException {
        checkIfKindPropertyExists(kind);
        JsonSchemaFactory factory = new JsonSchemaFactory();
        JSONObject jsonSchema = getSchemaJson(schemaUrl);
        getResourceProperties(kind, jsonSchema);

        return factory.getSchema(jsonSchema.toString());
    }

    private void getResourceProperties(String kind, JSONObject jsonSchema) {
        jsonSchema.put("properties" , jsonSchema.getJSONObject("resources")
                .getJSONObject(kind.replaceAll("\"", "").toLowerCase())
                .getJSONObject("properties"));
    }

    private void checkIfKindPropertyExists(String kind) {
        if(kind == null) {
            throw new JSONException("Invalid kind of resource or 'kind' is missing from resource definition");
        }
    }

    private URI prepareSchemaUrl(String schemaFile) throws URISyntaxException {
        return getClass().getResource(schemaFile).toURI();
    }

    private JsonNode geFileContent(File file) throws IOException {
        try (InputStream resourceStream = new FileInputStream(file)) {
            ObjectMapper jsonMapper = new ObjectMapper(new YAMLFactory());
            return jsonMapper.readTree(resourceStream);
        }
    }

    public JSONObject getSchemaJson(URI schemaUrl) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String rootNode = objectMapper.readValue(schemaUrl.toURL(), JsonNode.class).toString();
        JSONObject jsonObject = new JSONObject(rootNode);
        jsonObject.remove("id");
        return jsonObject;
    }

    private class ConstraintViolationImpl implements ConstraintViolation<ValidationMessage> {

        private ValidationMessage errorMsg;

        public ConstraintViolationImpl(ValidationMessage errorMsg) {
            this.errorMsg = errorMsg;
        }

        @Override
        public String getMessage() {
            return errorMsg.getMessage();
        }

        @Override
        public String getMessageTemplate() {
            return null;
        }

        @Override
        public ValidationMessage getRootBean() {
            return null;
        }

        @Override
        public Class<ValidationMessage> getRootBeanClass() {
            return null;
        }

        @Override
        public Object getLeafBean() {
            return null;
        }

        @Override
        public Object[] getExecutableParameters() {
            return new Object[0];
        }

        @Override
        public Object getExecutableReturnValue() {
            return null;
        }

        @Override
        public Path getPropertyPath() {
            return null;
        }

        @Override
        public Object getInvalidValue() {
            return null;
        }

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return null;
        }

        @Override
        public <U> U unwrap(Class<U> aClass) {
            return null;
        }

        @Override
        public String toString() {
            return "[message=" + getMessage().replaceFirst("[$]", "") +", violation type="+errorMsg.getType()+"]";
        }
    }
}
