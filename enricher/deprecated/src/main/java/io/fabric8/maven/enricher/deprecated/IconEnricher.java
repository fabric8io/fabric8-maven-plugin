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

package io.fabric8.maven.enricher.deprecated;

import com.google.common.io.Files;
import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.FileUtil;
import io.fabric8.maven.core.util.SpringBootConfigurationHelper;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;

import io.fabric8.maven.enricher.api.util.ProjectClassLoaders;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Enricher for adding icons to descriptors
 *
 * @author roland
 * @since 01/05/16
 */
public class IconEnricher extends BaseEnricher {

    private static String[] ICON_EXTENSIONS = new String[]{".svg", ".png", ".gif", ".jpg", ".jpeg"};

    private File templateTempDir;
    private File appConfigDir;
    private String iconBranch;

    // Available configuration keys
    private enum Config implements Configs.Key {

        templateTempDir,
        sourceDir,
        ref,
        maximumDataUrlSizeK   {{ d = "2"; }},
        urlPrefix,
        branch                {{ d = "master"; }},
        url;

        public String def() { return d; } protected String d;
    }

    public IconEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "f8-deprecated-icon");

        String baseDir = getContext().getProjectDirectory().getAbsolutePath();
        templateTempDir = new File(getConfig(Config.templateTempDir, baseDir + "/target/fabric8/template-workdir"));
        appConfigDir = new File(getConfig(Config.sourceDir, baseDir + "/src/main/fabric8"));
    }

    public Map<String, String> getAnnotations(String kind) {
        String iconUrl = getIconUrl(extractIconRef());
        if (iconUrl != null) {
            log.info("Adding icon for %s", kind.toLowerCase());
            log.verbose("Icon URL: %s", iconUrl);
            return Collections.singletonMap("fabric8.io/iconUrl", iconUrl);
        } else {
            log.debug("No icon file found for resources of type " + kind);
        }
        return null;
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ServiceBuilder>() {
            @Override
            public void visit(ServiceBuilder serviceBuilder) {
                serviceBuilder.editMetadata().addToAnnotations(getAnnotations(serviceBuilder.getKind())).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<DeploymentBuilder>() {
            @Override
            public void visit(DeploymentBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations(builder.getKind())).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<DeploymentConfigBuilder>() {
            @Override
            public void visit(DeploymentConfigBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations(builder.getKind())).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations(builder.getKind())).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations(builder.getKind())).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<DaemonSetBuilder>() {
            @Override
            public void visit(DaemonSetBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations(builder.getKind())).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<StatefulSetBuilder>() {
            @Override
            public void visit(StatefulSetBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations(builder.getKind())).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<JobBuilder>() {
            @Override
            public void visit(JobBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations(builder.getKind())).endMetadata();
            }
        });
    }

    // ====================================================================================================

    private String extractIconRef() {
        String iconRef = getConfig(Config.ref);
        if (StringUtils.isBlank(iconRef)) {
            iconRef = getDefaultIconRef();
        }
        return iconRef;
    }

    protected String getIconUrl(String iconRef) {
        String answer = getConfig(Config.url);
        if (StringUtils.isBlank(answer)) {
            try {
                if (templateTempDir != null) {
                    templateTempDir.mkdirs();
                    File iconFile = copyIconToFolder(iconRef, templateTempDir);
                    if (iconFile == null) {
                        copyAppConfigFiles(templateTempDir, appConfigDir);

                        // lets find the icon file...
                        for (String ext : ICON_EXTENSIONS) {
                            File file = new File(templateTempDir, "icon" + ext);
                            if (file.exists() && file.isFile()) {
                                iconFile = file;
                                break;
                            }
                        }
                    }
                    if (iconFile != null) {
                        answer = convertIconFileToURL(iconFile, iconRef);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load icon file: %s", e);
            }
        }
        if (StringUtils.isBlank(answer)) {
            // maybe its a common icon that is embedded in fabric8-console
            if (StringUtils.isNotBlank(iconRef)) {
                String embeddedIcon = embeddedIconsInConsole(iconRef, "img/icons/");
                if (embeddedIcon != null) {
                    return embeddedIcon;
                } else {
                    log.warn("Could not resolve iconRef: %s", iconRef);
                }
            }
        }
        return answer;
    }

    /**
     * Lets use the project and its classpath to try figure out what default icon to use
     *
     * @return the icon ref if we can detect one or return null
     */
    private String getDefaultIconRef() {
        ProjectClassLoaders cls = getContext().getProjectClassLoaders();
        if (cls.isClassInCompileClasspath(false, "io.fabric8.funktion.runtime.Main") ||
            getContext().hasDependency( "io.fabric8.funktion", null)) {
            return "funktion";
        }
        if (cls.isClassInCompileClasspath(false, "org.apache.camel.CamelContext")) {
            return "camel";
        }
        if (getContext().hasPlugin(null, SpringBootConfigurationHelper.SPRING_BOOT_MAVEN_PLUGIN_ARTIFACT_ID)  ||
            cls.isClassInCompileClasspath(false, "org.springframework.boot.SpringApplication")) {
            return "spring-boot";
        }
        if (cls.isClassInCompileClasspath(false, "org.springframework.core.Constants")) {
            return "spring";
        }
        if (cls.isClassInCompileClasspath(false, "org.vertx.java.core.Handler", "io.vertx.core.Handler")) {
            return "vertx";
        }

        if (getContext().hasPlugin("org.wildfly.swarm", "wildfly-swarm-plugin") ||
            getContext().hasDependency( "org.wildfly.swarm", null)) {
            return "wildfly-swarm";
        }
        if (getContext().hasPlugin( "io.thorntail", "thorntail-maven-plugin") ||
            getContext().hasDependency( "io.thorntail", null)) {
            // use the WildFly Swarm icon until there's a dedicated Thorntail icon
            // Thorntail is a new name of WildFly Swarm
            return "wildfly-swarm";
        }

        return null;
    }

    private File copyIconToFolder(String iconRef, File appBuildDir) throws IOException {
        if (StringUtils.isNotBlank(iconRef)) {
            File[] icons = appBuildDir.listFiles((dir, name) -> {
                if (name == null) {
                    return false;
                }
                String lower = name.toLowerCase();
                if (lower.startsWith("icon.")) {
                    for (String ext : ICON_EXTENSIONS) {
                        if (lower.endsWith(ext)) {
                            return true;
                        }
                    }
                }
                return false;
            });
            if (icons == null || icons.length == 0) {
                // lets copy the iconRef
                InputStream in = loadPluginResource(iconRef);
                if (in == null) {
                    // maybe it dont have extension so try to find it
                    for (String ext : ICON_EXTENSIONS) {
                        String name = iconRef + ext;
                        in = loadPluginResource(name);
                        if (in != null) {
                            iconRef = name;
                            break;
                        }
                    }
                }
                if (in != null) {
                    String fileName = "icon." + Files.getFileExtension(iconRef);
                    File outFile = new File(appBuildDir, fileName);
                    FileUtils.copyInputStreamToFile(in, outFile);
                    log.info("Generated icon file " + outFile + " from icon reference: " + iconRef);
                    return outFile;
                }
            }
        }
        return null;
    }

    private InputStream loadPluginResource(String iconRef) {
        InputStream answer = Thread.currentThread().getContextClassLoader().getResourceAsStream(iconRef);
        if (answer == null) {
            answer = getContext().getProjectClassLoaders().getCompileClassLoader().getResourceAsStream(iconRef);
        }
        if (answer == null) {
            answer = this.getClass().getResourceAsStream(iconRef);
        }
        return answer;
    }

    /**
     * Copies any local configuration files into the app directory
     */
    private void copyAppConfigFiles(File appBuildDir, File appConfigDir) throws IOException {
        File[] files = appConfigDir.listFiles();
        if (files != null) {
            appBuildDir.mkdirs();
            for (File file : files) {
                File outFile = new File(appBuildDir, file.getName());
                if (file.isDirectory()) {
                    copyAppConfigFiles(outFile, file);
                } else {
                    Files.copy(file, outFile);
                }
            }
        }
    }


    private String convertIconFileToURL(File iconFile, String iconRef) throws IOException {
        long length = iconFile.length();

        int sizeK = Math.round(length / 1024);

        byte[] bytes = FileUtils.readFileToByteArray(iconFile);
        byte[] encoded = Base64.encodeBase64(bytes);

        int base64SizeK = Math.round(encoded.length / 1024);

        if (base64SizeK < Configs.asInt(getConfig(Config.maximumDataUrlSizeK))) {
            String mimeType = URLConnection.guessContentTypeFromName(iconFile.getName());
            return "data:" + mimeType + ";charset=UTF-8;base64," + new String(encoded);
        } else {
            File iconSourceFile = new File(appConfigDir, iconFile.getName());
            if (iconSourceFile.exists()) {
                // The next line was 'rootDir' and thats probably wrong now,
                // but then after all the icon enricher is not used anymore anyway,
                // so don't bother much with this.
                // We should remove the deprecated package asap.
                File rootProjectFolder = getContext().getProjectDirectory();
                if (rootProjectFolder != null) {
                    String urlPrefix = getConfig(Config.urlPrefix);
                    if (StringUtils.isBlank(urlPrefix)) {
                        String relativePath = FileUtil.getRelativePath(rootProjectFolder, iconSourceFile).toString();
                        String relativeParentPath =
                            FileUtil.getRelativePath(rootProjectFolder, getContext().getProjectDirectory()).toString();
                        if (getContext() instanceof MavenEnricherContext) {
                            MavenEnricherContext mavenEnricherContext = (MavenEnricherContext) getContext();
                            final org.apache.maven.model.Scm scm = mavenEnricherContext.getProject().getScm();
                            if (scm != null) {
                                String url = scm.getUrl();
                                if (url != null) {
                                    String[] prefixes = {"http://github.com/", "https://github.com/"};
                                    for (String prefix : prefixes) {
                                        if (url.startsWith(prefix)) {
                                            url = "https://cdn.rawgit.com/" + url.substring(prefix.length());
                                            break;
                                        }
                                    }
                                    if (url.endsWith(relativeParentPath)) {
                                        url = url.substring(0, url.length() - relativeParentPath.length());
                                    }
                                    urlPrefix = url;
                                }
                            }
                        }
                        if (StringUtils.isBlank(urlPrefix)) {
                            log.warn(
                                "No iconUrlPrefix defined or could be found via SCM in the pom.xml so cannot add an icon URL!");
                        } else {
                            return String.format("%s/%s/%s", urlPrefix, getConfig(Config.branch), relativePath);
                        }
                    }
                }
            } else {
                String embeddedIcon = embeddedIconsInConsole(iconRef, "img/icons/");
                if (embeddedIcon != null) {
                    return embeddedIcon;
                } else {
                    log.warn("Cannot find url for icon to use %s", iconRef);
                }
            }
        }
        return null;
    }


    /**
     * To use embedded icons provided by the fabric8-console
     *
     * @param iconRef  name of icon file
     * @param prefix   prefix location for the icons in the fabric8-console
     * @return the embedded icon ref, or <tt>null</tt> if no embedded icon found to be used
     */
    protected String embeddedIconsInConsole(String iconRef, String prefix) {
        if (iconRef == null) {
            return null;
        }

        if (iconRef.startsWith("icons/")) {
            iconRef = iconRef.substring(6);
        }

        // special for fabric8 as its in a different dir
        if (iconRef.contains("META-INF/fabric8")) {
            return "img/fabric8_icon.svg";
        }

        if (iconRef.contains("activemq")) {
            return prefix + "activemq.svg";
        } else if (iconRef.contains("apiman")) {
            return prefix + "apiman.png";
        } else if (iconRef.contains("api-registry")) {
            return prefix + "api-registry.svg";
        } else if (iconRef.contains("brackets")) {
            return prefix + "brackets.svg";
        } else if (iconRef.contains("camel")) {
            return prefix + "camel.svg";
        } else if (iconRef.contains("chaos-monkey")) {
            return prefix + "chaos-monkey.png";
        } else if (iconRef.contains("docker-registry")) {
            return prefix + "docker-registry.png";
        } else if (iconRef.contains("elasticsearch")) {
            return prefix + "elasticsearch.png";
        } else if (iconRef.contains("fluentd")) {
            return prefix + "fluentd.png";
        } else if (iconRef.contains("forge")) {
            return prefix + "forge.svg";
        } else if (iconRef.contains("funktion")) {
            return prefix + "funktion.png";
        } else if (iconRef.contains("gerrit")) {
            return prefix + "gerrit.png";
        } else if (iconRef.contains("gitlab")) {
            return prefix + "gitlab.svg";
        } else if (iconRef.contains("gogs")) {
            return prefix + "gogs.png";
        } else if (iconRef.contains("grafana")) {
            return prefix + "grafana.png";
        } else if (iconRef.contains("hubot-irc")) {
            return prefix + "hubot-irc.png";
        } else if (iconRef.contains("hubot-letschat")) {
            return prefix + "hubot-letschat.png";
        } else if (iconRef.contains("hubot-notifier")) {
            return prefix + "hubot-notifier.png";
        } else if (iconRef.contains("hubot-slack")) {
            return prefix + "hubot-slack.png";
        } else if (iconRef.contains("image-linker")) {
            return prefix + "image-linker.svg";
        } else if (iconRef.contains("javascript")) {
            return prefix + "javascript.png";
        } else if (iconRef.contains("java")) {
            return prefix + "java.svg";
        } else if (iconRef.contains("jenkins")) {
            return prefix + "jenkins.svg";
        } else if (iconRef.contains("jetty")) {
            return prefix + "jetty.svg";
        } else if (iconRef.contains("karaf")) {
            return prefix + "karaf.svg";
        } else if (iconRef.contains("keycloak")) {
            return prefix + "keycloak.svg";
        } else if (iconRef.contains("kibana")) {
            return prefix + "kibana.svg";
        } else if (iconRef.contains("kiwiirc")) {
            return prefix + "kiwiirc.png";
        } else if (iconRef.contains("letschat")) {
            return prefix + "letschat.png";
        } else if (iconRef.contains("mule")) {
            return prefix + "mule.svg";
        } else if (iconRef.contains("nexus")) {
            return prefix + "nexus.png";
        } else if (iconRef.contains("node")) {
            return prefix + "node.svg";
        } else if (iconRef.contains("orion")) {
            return prefix + "orion.png";
        } else if (iconRef.contains("prometheus")) {
            return prefix + "prometheus.png";
        } else if (iconRef.contains("django") || iconRef.contains("python")) {
            return prefix + "python.png";
        } else if (iconRef.contains("spring-boot")) {
            return prefix + "spring-boot.svg";
        } else if (iconRef.contains("taiga")) {
            return prefix + "taiga.png";
        } else if (iconRef.contains("tomcat")) {
            return prefix + "tomcat.svg";
        } else if (iconRef.contains("tomee")) {
            return prefix + "tomee.svg";
        } else if (iconRef.contains("vertx")) {
            return prefix + "vertx.svg";
        } else if (iconRef.contains("wildfly")) {
            return prefix + "wildfly.svg";
        } else if (iconRef.contains("wildfly-swarm")) {
            return prefix + "wildfly-swarm.png";
        } else if (iconRef.contains("weld")) {
            return prefix + "weld.svg";
        } else if (iconRef.contains("zipkin")) {
            return prefix + "zipkin.png";
        }

        return null;
    }

}
