package io.fabric8.maven.core.util.kubernetes;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Config;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Context;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.maven.core.util.ResourceFileType;
import io.fabric8.maven.core.util.ResourceUtil;
import io.fabric8.openshift.api.model.Template;
import org.apache.commons.lang3.StringUtils;

/**
 * @author roland
 * @since 23.05.17
 */
public class KubernetesHelper {

    /**
     * Validates that the given value is valid according to the kubernetes ID parsing rules, throwing an exception if not.
     */
    public static String validateKubernetesId(String currentValue, String description) throws IllegalArgumentException {
        if (StringUtils.isBlank(currentValue)) {
            throw new IllegalArgumentException("No " + description + " is specified!");
        }
        int size = currentValue.length();
        for (int i = 0; i < size; i++) {
            char ch = currentValue.charAt(i);
            if (Character.isUpperCase(ch)) {
                throw new IllegalArgumentException("Invalid upper case letter '" + ch + "' at index " + i + " for " + description + " value: " + currentValue);
            }
        }
        return currentValue;
    }


        /**
     * Loads the Kubernetes JSON and converts it to a list of entities
     */
    @SuppressWarnings("unchecked")
    public static List<HasMetadata> toItemList(Object entity) throws IOException {
        if (entity instanceof List) {
            return (List<HasMetadata>) entity;
        } else if (entity instanceof HasMetadata[]) {
            HasMetadata[] array = (HasMetadata[]) entity;
            return Arrays.asList(array);
        } else if (entity instanceof KubernetesList) {
            KubernetesList config = (KubernetesList) entity;
            return config.getItems();
        } else if (entity instanceof Template) {
            Template objects = (Template) entity;
            return objects.getObjects();
        } else {
            List<HasMetadata> answer = new ArrayList<>();
            if (entity instanceof HasMetadata) {
                answer.add((HasMetadata) entity);
            }
            return answer;
        }
    }

    public static Map<String, String> getOrCreateAnnotations(HasMetadata entity) {
        ObjectMeta metadata = getOrCreateMetadata(entity);
        Map<String, String> answer = metadata.getAnnotations();
        if (answer == null) {
            // use linked so the annotations can be in the FIFO order
            answer = new LinkedHashMap<>();
            metadata.setAnnotations(answer);
        }
        return answer;
    }

    public static ObjectMeta getOrCreateMetadata(HasMetadata entity) {
        ObjectMeta metadata = entity.getMetadata();
        if (metadata == null) {
            metadata = new ObjectMeta();
            entity.setMetadata(metadata);
        }
        return metadata;
    }

    public static Map<String, String> getOrCreateLabels(HasMetadata entity) {
        ObjectMeta metadata = getOrCreateMetadata(entity);
        Map<String, String> answer = metadata.getLabels();
        if (answer == null) {
            // use linked so the annotations can be in the FIFO order
            answer = new LinkedHashMap<>();
            metadata.setLabels(answer);
        }
        return answer;
    }

        /**
     * Returns the resource version for the entity or null if it does not have one
     */
    public static String getResourceVersion(HasMetadata entity) {
        if (entity != null) {
            ObjectMeta metadata = entity.getMetadata();
            if (metadata != null) {
                String resourceVersion = metadata.getResourceVersion();
                if (StringUtils.isNotBlank(resourceVersion)) {
                    return resourceVersion;
                }
            }
        }
        return null;
    }

    public static Map<String, String> getLabels(HasMetadata entity) {
        if (entity != null) {
            return getLabels(entity.getMetadata());
        }
        return Collections.EMPTY_MAP;
    }

    /**
     * Returns the labels of the given metadata object or an empty map if the metadata or labels are null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getLabels(ObjectMeta metadata) {
        if (metadata != null) {
            Map<String, String> labels = metadata.getLabels();
            if (labels != null) {
                return labels;
            }
        }
        return Collections.EMPTY_MAP;
    }

    public static String getName(HasMetadata entity) {
        if (entity != null) {
            return getName(entity.getMetadata());
        } else {
            return null;
        }
    }

    public static String getName(ObjectMeta entity) {
        if (entity != null) {
            for (String name : new String[]{
                entity.getName(),
                getAdditionalPropertyText(entity.getAdditionalProperties(), "id"),
                entity.getUid()
            }) {
                if (StringUtils.isNotBlank(name)) {
                    return name;
                }
            }
        }
        return null;
    }

    public static String getNamespace(ObjectMeta entity) {
        if (entity != null) {
            return entity.getNamespace();
        } else {
            return null;
        }
    }

    public static String getNamespace(HasMetadata entity) {
        if (entity != null) {
            return getNamespace(entity.getMetadata());
        } else {
            return null;
        }
    }

    /**
     * Returns the kind of the entity
     */
    public static String getKind(HasMetadata entity) {
        if (entity != null) {
            // TODO use reflection to find the kind?
            if (entity instanceof KubernetesList) {
                return "List";
            } else {
                return entity.getClass().getSimpleName();
            }
        } else {
            return null;
        }
    }


        /**
     * Creates an IntOrString from the given string which could be a number or a name
     */
    public static IntOrString createIntOrString(int intVal) {
        IntOrString answer = new IntOrString();
        answer.setIntVal(intVal);
        answer.setKind(0);
        return answer;
    }

    /**
     * Creates an IntOrString from the given string which could be a number or a name
     */
    public static IntOrString createIntOrString(String nameOrNumber) {
        if (StringUtils.isBlank(nameOrNumber)) {
            return null;
        } else {
            IntOrString answer = new IntOrString();
            Integer intVal = null;
            try {
                intVal = Integer.parseInt(nameOrNumber);
            } catch (Exception e) {
                // ignore invalid number
            }
            if (intVal != null) {
                answer.setIntVal(intVal);
                answer.setKind(0);
            } else {
                answer.setStrVal(nameOrNumber);
                answer.setKind(1);
            }
            return answer;
        }
    }



    /**
     * Returns true if the pod is running
     */
    public static boolean isPodRunning(Pod pod) {
        return isInPodPhase(pod, "run");
    }

    public static boolean isPodWaiting(Pod pod) {
        return isInPodPhase(pod, "wait");
    }

    /**
     * Returns true if the pod is running and ready
     */
    public static boolean isPodReady(Pod pod) {
        if (!isPodRunning(pod)) {
            return false;
        }

        PodStatus podStatus = pod.getStatus();
        if (podStatus == null) {
            return true;
        }

        List<PodCondition> conditions = podStatus.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        // Check "ready" condition
        for (PodCondition condition : conditions) {
            if ("ready".equalsIgnoreCase(condition.getType())) {
                return Boolean.parseBoolean(condition.getStatus());
            }
        }

        return true;
    }

    private static boolean isInPodPhase(Pod pod, String phase) {
        return getPodPhase(pod).startsWith(phase);
    }

    static String getPodPhase(Pod pod) {
        if (pod != null) {
            PodStatus podStatus = pod.getStatus();
            if (podStatus != null) {
                String actualPhase = podStatus.getPhase();
                return actualPhase != null ? actualPhase : "";
            }
        }
        return "";
    }


    public static List<Container> getContainers(Pod pod) {
        if (pod != null) {
            PodSpec podSpec = pod.getSpec();
            return getContainers(podSpec);

        }
        return Collections.EMPTY_LIST;
    }

    @SuppressWarnings("unchecked")
    public static List<Container> getContainers(PodSpec podSpec) {
        if (podSpec != null) {
            return podSpec.getContainers();
        }
        return Collections.EMPTY_LIST;
    }

    private static String getAdditionalPropertyText(Map<String, Object> additionalProperties, String name) {
        if (additionalProperties != null) {
            Object value = additionalProperties.get(name);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }


    public static String getDefaultNamespace() {
        String ns = new ConfigBuilder().build().getNamespace();
        return ns != null ? ns : "default";
    }

    public static String currentUserName() {
        Config config = parseConfigs();
        if (config != null) {
            Context context = getCurrentContext(config);
            if (context != null) {
                String user = context.getUser();
                if (user != null) {
                    String[] parts = user.split("/");
                    if (parts.length > 0) {
                        return parts[0];
                    }
                    return user;
                }
            }
        }
        return null;
    }

    private static Config parseConfigs() {
        File file = getKubernetesConfigFile();
        if (file.exists() && file.isFile()) {
            try {
                return ResourceUtil.load(file, Config.class, ResourceFileType.yaml);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Returns the current context in the given config
     */
    private static Context getCurrentContext(Config config) {
        String contextName = config.getCurrentContext();
        if (contextName != null) {
            List<NamedContext> contexts = config.getContexts();
            if (contexts != null) {
                for (NamedContext context : contexts) {
                    if (Objects.equals(contextName, context.getName())) {
                        return context.getContext();
                    }
                }
            }
        }
        return null;
    }

    private static File getKubernetesConfigFile() {
        String file = System.getProperty("kubernetes.config.file");
        if (file != null) {
            return new File(file);
        }
        file = System.getenv("KUBECONFIG");
        if (file != null) {
            return new File(file);
        }
        String homeDir = System.getProperty("user.home", ".");
        return new File(homeDir, ".kube/config");
    }
}
