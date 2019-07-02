package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.Port;
import io.fabric8.maven.core.util.FatJarDetector;
import io.fabric8.maven.docker.util.DeepCopy;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class JibBuildConfiguration {

    private Map<String, String> envMap;

    private Credential credential;

    private List<String> ports;

    private List<Path> dependencyList;

    private String from;

    private String target;

    private String pushRegistry;

    private Path fatJarPath;

    private JibBuildConfiguration() {
    }

    public Map<String, String> getEnvMap() {
        return envMap;
    }

    public Credential getCredential() {return credential; }

    public List<String> getPorts() {return ports;}

    public String getFrom() {return from;}

    public String getPushRegistry() {return pushRegistry;}

    public String getTargetImage() {return target;}

    public List<Path> getDependencyList() {return dependencyList;}

    public Path getFatJar() {return fatJarPath;}

    public static class Builder {
        private final JibBuildConfiguration configutil;

        public Builder() {
            this(null);
        }

        public Builder(JibBuildConfiguration that) {
            if (that == null) {
                this.configutil = new JibBuildConfiguration();
            } else {
                this.configutil = DeepCopy.copy(that);
            }
        }

        public Builder envMap(Map<String, String> envMap) {
            configutil.envMap = envMap;
            return this;
        }

        public Builder credential(Credential credential) {
            configutil.credential = credential;
            return this;
        }

        public Builder ports(List<String> ports) {
            configutil.ports = ports;
            return this;
        }

        public Builder depList(List<Path> dependencyList) {
            configutil.dependencyList = dependencyList;
            return this;
        }

        public Builder from(String from) {
            configutil.from = from;
            return this;
        }

        public Builder targetImage (String imageName) {
            configutil.target = imageName;
            return this;
        }

        public Builder pushRegistry(String pushRegistry) {
            configutil.pushRegistry = pushRegistry;
            return this;
        }

        public Builder buildDirectory(String buildDir) {
            configutil.fatJarPath = JibBuildServiceUtil.getFatJar(buildDir);
            return this;
        }

        public JibBuildConfiguration build() {
            return configutil;
        }
    }
}