/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.fabric8;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.fabric8.config.*;
import io.fabric8.maven.fabric8.enricher.Enricher;
import io.fabric8.maven.fabric8.support.*;
import io.fabric8.maven.fabric8.util.MavenBuildContext;
import io.fabric8.maven.fabric8.util.MavenUtils;
import io.fabric8.maven.fabric8.util.PluginServiceFactory;
import io.fabric8.utils.Files;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import static io.fabric8.maven.fabric8.config.ResourceType.yaml;

/**
 * Generates or copies the Kubernetes JSON file and attaches it to the build so its
 * installed and released to maven repositories like other build artifacts.
 */
@Mojo(name = "resource", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ResourceMojo extends AbstractMojo {

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * Whether to skip the run or not
     */
    @Parameter(property = "fabric8.skip")
    private boolean skip = false;

    /**
     * Folder where to find project specific files
     */
    @Parameter(property = "fabric8.resourceDir", defaultValue = "${basedir}/src/main/fabric8")
    private File resourceDir;

    /**
     * The artifact type for attaching the generated resource file to the project.
     * Can be either 'json' or 'yaml'
     */
    @Parameter(property = "fabric8.resourceType")
    private ResourceType resourceType = yaml;

    /**
     * The artifact classifier for attaching the generated kubernetes resource file to the project
     */
    @Parameter(property = "fabric8.resourceClassifier")
    private String resourceClassifier = "kubernetes";

    /**
     * The generated kubernetes JSON file
     */
    @Parameter(property = "fabric8.targetDir", defaultValue = "${project.build.outputDirectory}")
    private File target;

    // Kubernetes specific configuration for this plugin
    @Parameter
    private KubernetesConfiguration kubernetes;

    // Reusing image configuration from d-m-p
    @Parameter
    private List<ImageConfiguration> images;

    // List of enrichers used for customizing the generated deployment descriptors
    private List<Enricher> enrichers;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        initEnrichers();

        if (!skip && !kubernetes.isSkip() &&  (!isPomProject() || hasConfigDir())) {
            KubernetesList resources = generateResourceDescriptor();
            writeResourceDescriptor(resources, new File(target,"fabric8"));
        }
    }

    // ==================================================================================

    private void initEnrichers() {
        enrichers = PluginServiceFactory.createServiceObjects(new MavenBuildContext(project),
                                                              "fabric8-enricher-default", "fabric8-enricher");
        Collections.reverse(enrichers);
    }

    private KubernetesList generateResourceDescriptor() throws MojoExecutionException {

        Map<String,String> labelMap = extractLabels();
        AnnotationConfiguration annoConfig = kubernetes.getAnnotations();

        KubernetesListBuilder builder = new KubernetesListBuilder();
        builder
            .addNewReplicationControllerItem()
              .withMetadata(createRcMetaData(labelMap,annoConfig))
              .withSpec(createRcSpec(labelMap,annoConfig))
            .endReplicationControllerItem();

        addServices(builder, labelMap);

        return builder.build();
    }

    private void addServices(KubernetesListBuilder builder, Map<String, String> labelMap) throws MojoExecutionException {

        List<ServiceConfiguration> services = kubernetes.getServices();
        for (ServiceConfiguration service : services) {
            AnnotationConfiguration annos = kubernetes.getAnnotations();
            Map<String, String> serviceAnnotations = annos != null ? annos.getService() : null;
            Map<String, String> selector = new HashMap<>(labelMap);

            ServiceBuilder serviceBuilder = new ServiceBuilder()
                .withNewMetadata()
                .withName(service.getName())
                .withLabels(labelMap)
                .withAnnotations(serviceAnnotations)
                .endMetadata();

            ServiceFluent.SpecNested<ServiceBuilder> serviceSpecBuilder =
                serviceBuilder.withNewSpec().withSelector(selector);

            List<ServicePort> servicePorts = new ArrayList<>();
            for (ServiceConfiguration.Port port : service.getPorts()) {
                ServicePort servicePort = new ServicePortBuilder()
                    .withProtocol(port.getProtocol().name())
                    .withTargetPort(new IntOrString(port.getTargetPort()))
                    .withPort(port.getPort())
                    .withNodePort(port.getNodePort())
                    .build();
                servicePorts.add(servicePort);
            }

            if (!servicePorts.isEmpty()) {
                serviceSpecBuilder.withPorts(servicePorts);
            }

            if (service.isHeadless()) {
                serviceSpecBuilder.withClusterIP("None");
            }

            if (!Strings.isNullOrBlank(service.getType())) {
                serviceSpecBuilder.withType(service.getType());
            }
            serviceSpecBuilder.endSpec();

            if (service.isHeadless() || !servicePorts.isEmpty()) {
                builder = builder.addToServiceItems(serviceBuilder.build());
            }
        }
    }

    private void writeResourceDescriptor(KubernetesList kubernetesList, File target) {
        try {
            ObjectMapper mapper = resourceType.getObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)
                .disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
            String serialized = mapper.writeValueAsString(kubernetesList);
            Files.writeToFile(resourceType.addExtension(target), serialized, Charset.defaultCharset());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to generate fabric8 descriptor", e);
        }
    }

    private ReplicationControllerSpec createRcSpec(Map<String, String> labelMap, AnnotationConfiguration annoConfig)
        throws MojoExecutionException {
        return new ReplicationControllerSpecBuilder()
            .withReplicas(kubernetes.getReplicas())
            .withSelector(labelMap)
            .withTemplate(createPodTemplate(labelMap,annoConfig))
            .build();
    }

    private PodTemplateSpec createPodTemplate(Map<String, String> labelMap, AnnotationConfiguration annoConfig) throws MojoExecutionException {

        return new PodTemplateSpecBuilder()
            .withMetadata(createPodMetaData(labelMap,annoConfig))
            .withSpec(createPodSpec())
            .build();
    }

    private PodSpec createPodSpec() throws MojoExecutionException {

        return new PodSpecBuilder()
            .withServiceAccountName(kubernetes.getServiceAccount())
            .withContainers(createContainers())
            .withVolumes(getVolumes())
            .build();
    }

    private List<Container> createContainers() throws MojoExecutionException {

        List<Container> ret = new ArrayList<>();
        for (ImageConfiguration imageConfig : images) {
            if (imageConfig.getBuildConfiguration() != null) {
                Container container = new ContainerBuilder()
                    .withName(getKubernetesContainerName(imageConfig))
                    .withImage(imageConfig.getName())
                    .withImagePullPolicy(getImagePullPolicy())
                    .withEnv(getEnvironmentVariables())
                    .withSecurityContext(createSecurityContext())
                    .withPorts(getContainerPorts(imageConfig))
                    .withVolumeMounts(getVolumeMounts())
                    .withLivenessProbe(getLivenessProbe())
                    .withReadinessProbe(getReadinessProbe())
                    .build();
                ret.add(container);
            }
        }
        return ret;
    }

    private SecurityContext createSecurityContext() {
        return new SecurityContextBuilder()
            .withPrivileged(kubernetes.isContainerPrivileged())
            .build();
    }


    private ObjectMeta createPodMetaData(Map<String, String> labelMap, AnnotationConfiguration annoConfig) {
        return new ObjectMetaBuilder()
            .withLabels(labelMap)
            .withAnnotations(annoConfig.getPod())
            .build();
    }

    private ObjectMeta createRcMetaData(Map<String, String> labelMap, AnnotationConfiguration annoConfig) {
        return new ObjectMetaBuilder()
            .withName(KubernetesHelper.validateKubernetesId(kubernetes.getRcName(),"replication controller name"))
            .withLabels(labelMap)
            .withAnnotations(annoConfig.getRc())
            .build();
    }

    // ===================================================================================================


    private Map<String, String> extractLabels() {
        Map <String, String> ret = new HashMap<>();
        for (Enricher enricher : enrichers) {
            putAllIfNotNull(ret, enricher.getLabels());
        }
        putAllIfNotNull(ret, kubernetes.getLabels());
        return ret;
    }

    private void putAllIfNotNull(Map<String, String> ret, Map<String, String> labels) {
        if (labels != null) {
            ret.putAll(labels);
        }
    }

    private Probe getLivenessProbe() {
        return getProbe(kubernetes.getLiveness());
    }

    private Probe getReadinessProbe() {
        return getProbe(kubernetes.getReadiness());
    }

    private Probe getProbe(ProbeConfiguration probeConfig)  {
        if (probeConfig == null) {
            return null;
        }

        Probe probe = new Probe();
        Integer initialDelaySeconds = probeConfig.getInitialDelaySeconds();
        if (initialDelaySeconds != null) {
            probe.setInitialDelaySeconds(initialDelaySeconds);
        }
        Integer timeoutSeconds = probeConfig.getTimeoutSeconds();
        if (timeoutSeconds != null) {
            probe.setTimeoutSeconds(timeoutSeconds);
        }
        HTTPGetAction getAction = getHTTPGetAction(probeConfig.getGetUrl());
        if (getAction != null) {
            probe.setHttpGet(getAction);
            return probe;
        }
        ExecAction execAction = getExecAction(probeConfig.getExec());
        if (execAction != null) {
            probe.setExec(execAction);
            return probe;
        }
        TCPSocketAction tcpSocketAction = getTCPSocketAction(probeConfig.getTcpPort());
        if (tcpSocketAction != null) {
            probe.setTcpSocket(tcpSocketAction);
            return probe;
        }

        return null;
    }

    private HTTPGetAction getHTTPGetAction(String getUrl) {
        if (getUrl == null) {
            return null;
        }
        try {
            URL url = new URL(getUrl);
            return new HTTPGetAction(url.getHost(),
                                     null /* headers */,
                                     url.getPath(),
                                     new IntOrString(url.getPort()),
                                     url.getProtocol());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL " + getUrl + " given for HTTP GET readiness check");
        }
    }

    private TCPSocketAction getTCPSocketAction(String port) {
        if (port != null) {
            IntOrString portObj = new IntOrString(port);
            try {
                Integer portInt = Integer.parseInt(port);
                portObj.setIntVal(portInt);
            } catch (NumberFormatException e) {
                portObj.setStrVal(port);
            }
            return new TCPSocketAction(portObj);
        }
        return null;
    }

    private ExecAction getExecAction(String execCmd) {
        if (Strings.isNotBlank(execCmd)) {
            List<String> splitCommandLine = Commandline.translateCommandline(execCmd);
            if (!splitCommandLine.isEmpty()) {
                return new ExecAction(splitCommandLine);
            }
        }
        return null;
    }

    private String getImagePullPolicy() {
        String pullPolicy = kubernetes.getImagePullPolicy();
        String version = project.getVersion();
        if (Strings.isNullOrBlank(pullPolicy) &&
            version != null && version.endsWith("SNAPSHOT")) {
            // TODO: Is that what we want ?
            return "PullAlways";
        }
        return pullPolicy;
    }

    private String getKubernetesContainerName(ImageConfiguration imageConfig) {
        String alias = imageConfig.getAlias();
        if (alias != null) {
            return alias;
        }

        // lets generate it from the docker user and the camelCase artifactId
        String groupPrefix = null;
        String imageName = imageConfig.getName();
        if (Strings.isNotBlank(imageName)) {
            String[] paths = imageName.split("/");
            if (paths.length == 2) {
                groupPrefix = paths[0];
            } else if (paths.length == 3) {
                groupPrefix = paths[1];
            }
        }
        if (Strings.isNullOrBlank(groupPrefix)) {
            groupPrefix = project.getGroupId();
        }
        return groupPrefix + "-" + project.getArtifactId();
    }

    private List<ContainerPort> getContainerPorts(ImageConfiguration imageConfig) {
        RunImageConfiguration runConfig = imageConfig.getRunConfiguration();
        List<String> ports = runConfig.getPorts();
        if (ports != null) {
            List<ContainerPort> ret = new ArrayList<>();
            PortMapping portMapping = new PortMapping(ports, project.getProperties());
            JSONArray portSpecs = portMapping.toJson();
            for (int i = 0; i < portSpecs.length(); i ++) {
                JSONObject portSpec = portSpecs.getJSONObject(i);
                ret.add(createContainerPort(portSpec));
            }
            return ret;
        } else {
            return null;
        }
    }

    private EditableContainerPort createContainerPort(JSONObject portSpec) {
        ContainerPortBuilder portBuilder = new ContainerPortBuilder()
            .withContainerPort(portSpec.getInt("containerPort"));
        if (portSpec.has("hostPort")) {
            portBuilder.withHostPort(portSpec.getInt("hostPort"));
        }
        if (portSpec.has("protocol")) {
            portBuilder.withProtocol(portSpec.getString("protocol"));
        }
        if (portSpec.has("hostIP")) {
            portBuilder.withHostIP(portSpec.getString("hostIP"));
        }
        return portBuilder.build();
    }

    private List<EnvVar> getEnvironmentVariables() throws MojoExecutionException {
        List<EnvVar> ret = new ArrayList<>();

        Map<String, String> envs = getExportedEnvironmentVariables();
        Map<String, EnvVar> envMap = convertToEnvVarMap(envs);
        ret.addAll(envMap.values());

        ret.add(
            new EnvVarBuilder()
                .withName("KUBERNETES_NAMESPACE")
                .withNewValueFrom()
                  .withNewFieldRef()
                     .withFieldPath("metadata.namespace")
                  .endFieldRef()
                .endValueFrom()
                .build());

        return ret;
    }

    private Map<String, EnvVar> convertToEnvVarMap(Map<String, String> envs) {
        Map<String, EnvVar> envMap = new HashMap<>();
        for (Map.Entry<String, String> entry : envs.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            if (name != null) {
                EnvVar env = new EnvVarBuilder().withName(name).withValue(value).build();
                envMap.put(name,env);
            }
        }
        return envMap;
    }

    private Map<String, String> getExportedEnvironmentVariables() throws MojoExecutionException {
        try {
            Map<String, String> ret = getEnvironmentVarsFromJsonSchema();
            ret.putAll(kubernetes.getEnv());
            return ret;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load environment variable json schema files: " + e, e);
        }
    }

    private Map<String, String> getEnvironmentVarsFromJsonSchema() throws IOException, MojoExecutionException {
        Map<String, String> ret = new TreeMap<>();
        JsonSchema schema = getEnvironmentVariableJsonSchema();
        Map<String, JsonSchemaProperty> properties = schema.getProperties();
        Set<Map.Entry<String, JsonSchemaProperty>> entries = properties.entrySet();
        for (Map.Entry<String, JsonSchemaProperty> entry : entries) {
            String name = entry.getKey();
            String value = entry.getValue().getDefaultValue();
            ret.put(name, value != null ? value : "");
        }
        return ret;
    }

    private List<Volume> getVolumes() {

        List<VolumeConfiguration> volumeConfigs = kubernetes.getVolumes();

        List<Volume> ret = new ArrayList<>();
        if (volumeConfigs != null) {
            for (VolumeConfiguration volumeConfig : volumeConfigs) {
                VolumeType type = VolumeType.typeFor(volumeConfig.getType());
                if (type != null) {
                    ret.add(type.fromConfig(volumeConfig));
                }
            }
        }
        return ret;
    }

    private List<VolumeMount> getVolumeMounts() {
        List<VolumeConfiguration> volumeConfigs = kubernetes.getVolumes();

        List<VolumeMount> ret = new ArrayList<>();
        if (volumeConfigs != null) {
            for (VolumeConfiguration volumeConfig : volumeConfigs) {
                List<String> mounts = volumeConfig.getMounts();
                if (mounts != null) {
                    for (String mount : mounts) {
                        ret.add(new VolumeMountBuilder()
                                    .withName(volumeConfig.getName())
                                    .withMountPath(mount)
                                    .withReadOnly(false).build());
                    }
                }
            }
        }
        return ret;
    }

    private boolean hasConfigDir() {
        return resourceDir.isDirectory();
    }

    private boolean isPomProject() {
        return "pom".equals(project.getPackaging());
    }

    private JsonSchema getEnvironmentVariableJsonSchema() throws IOException, MojoExecutionException {
        JsonSchema schema = JsonSchemas.loadEnvironmentSchemas(MavenUtils.getCompileClassLoader(project),
                                                               project.getBuild().getOutputDirectory());
        if (schema == null) {
            getLog().debug("No environment schemas found for file: " + JsonSchemas.ENVIRONMENT_SCHEMA_FILE);
            schema = new JsonSchema();
        }
        JsonSchemas.addEnvironmentVariables(schema, kubernetes.getEnv());
        return schema;
    }
}
