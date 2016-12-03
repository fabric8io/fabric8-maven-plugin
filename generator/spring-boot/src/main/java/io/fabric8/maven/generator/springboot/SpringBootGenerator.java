/*
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

package io.fabric8.maven.generator.springboot;

import com.google.common.base.Strings;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.SpringBootProperties;
import io.fabric8.maven.core.util.SpringBootUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.generator.javaexec.FatJarDetector;
import io.fabric8.maven.generator.javaexec.JavaExecGenerator;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static io.fabric8.maven.core.util.SpringBootProperties.DEV_TOOLS_REMOTE_SECRET;
import static io.fabric8.maven.generator.springboot.SpringBootGenerator.Config.color;

/**
 * @author roland
 * @since 15/05/16
 */
public class SpringBootGenerator extends JavaExecGenerator {

    private static final String SPRING_BOOT_MAVEN_PLUGIN_GA = "org.springframework.boot:spring-boot-maven-plugin";
    private static final String SPRING_BOOT_DEVTOOLS_ENTRY = "fabric8-spring-devtools/spring-boot-devtools.jar";
    private static final String DEFAULT_SERVER_PORT = "8080";

    public enum Config implements Configs.Key {
        color {{ d = "false"; }};

        public String def() { return d; } protected String d;
    }

    public SpringBootGenerator(GeneratorContext context) {
        super(context, "spring-boot");
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddImageConfiguration(configs)
               && MavenUtil.hasPlugin(getProject(), SPRING_BOOT_MAVEN_PLUGIN_GA);
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs, boolean isPrePackagePhase) throws MojoExecutionException {
        if (!isPrePackagePhase && getContext().isWatchMode()) {
            ensureSpringDevToolSecretToken();
            addDevToolsToFatJar(configs);
        }
        return super.customize(configs, isPrePackagePhase);
    }

    @Override
    protected Map<String, String> getEnv(boolean isPrePackagePhase) throws MojoExecutionException {
        Map<String, String> ret = super.getEnv(isPrePackagePhase);
        if (Boolean.parseBoolean(getConfig(color))) {
            ret.put("JAVA_OPTIONS","-Dspring.output.ansi.enabled=" + getConfig(color));
        }
        return ret;
    }

    @Override
    protected boolean isFatJar() throws MojoExecutionException {
        if (!hasMainClass() && isSpringBootRepackage()) {
            return true;
        }
        return super.isFatJar();
    }

    @Override
    protected List<String> extractPorts() {
        List<String> answer = new ArrayList<>();
        Properties properties = SpringBootUtil.getSpringBootApplicationProperties(this.getProject());
        String port = properties.getProperty(SpringBootProperties.SERVER_PORT, DEFAULT_SERVER_PORT);
        addPortIfValid(answer, getConfig(JavaExecGenerator.Config.webPort, port));
        addPortIfValid(answer, getConfig(JavaExecGenerator.Config.jolokiaPort));
        addPortIfValid(answer, getConfig(JavaExecGenerator.Config.prometheusPort));
        return answer;
    }

    // =============================================================================

    private void ensureSpringDevToolSecretToken() throws MojoExecutionException {
        Properties properties = SpringBootUtil.getSpringBootApplicationProperties(getProject());
        String remoteSecret = properties.getProperty(DEV_TOOLS_REMOTE_SECRET);
        if (Strings.isNullOrEmpty(remoteSecret)) {
            addSecretTokenToApplicationProperties();
        }
    }

    private void addDevToolsToFatJar(List<ImageConfiguration> configs) throws MojoExecutionException {
        if (isFatJar()) {
            File target = getFatJarFile();
            String devToolsFile = getDevToolsJarContainingJarFile();
            try (FileSystem devToolsJarFs = FileSystems.newFileSystem(new URI("jar:" + devToolsFile),
                                                                      Collections.<String,String>emptyMap())) {
                Path resourcePath = devToolsJarFs.getPath(SPRING_BOOT_DEVTOOLS_ENTRY);
                copyDevToolsJarToFatTargetJar(resourcePath, target);
            } catch (URISyntaxException | IOException e) {
                throw new MojoExecutionException("Failed to add " + SPRING_BOOT_DEVTOOLS_ENTRY + " to temp file " + target + ". " + e, e);
            }
        }
    }

    private File getFatJarFile() throws MojoExecutionException {
        FatJarDetector.Result fatJarDetectResult = detectFatJar();
        if (fatJarDetectResult == null) {
            throw new MojoExecutionException("No fat jar built yet. Please ensure that the 'package' phase has run");
        }
        return fatJarDetectResult.getArchiveFile();
    }

    private String getDevToolsJarContainingJarFile() throws MojoExecutionException {
        URL resource = getClass().getClassLoader().getResource(SPRING_BOOT_DEVTOOLS_ENTRY);
        if (resource == null) {
            throw new MojoExecutionException("Could not find resource " + SPRING_BOOT_DEVTOOLS_ENTRY + " on the classpath!");
        }
        try {
            String all = resource.toURI().getRawSchemeSpecificPart();
            int idx = all.indexOf("!/");
            if(idx == -1) {
                throw new MojoExecutionException("Internal error: Cannot extract tools jar from internal jar");
            }
            return all.substring(0,idx);
        } catch (URISyntaxException e) {
            throw new MojoExecutionException("Invalid URI syntax of " + resource, e);
        }
    }

    private void copyDevToolsJarToFatTargetJar(Path resourcePath, File target) throws IOException {
        File tmpZip = File.createTempFile(target.getName(), null);
        tmpZip.delete();
        if (!target.renameTo(tmpZip)) {
            throw new IOException("Could not make temp file (" + target.getName() + ")");
        }
        byte[] buffer = new byte[8192];
        ZipInputStream zin = new ZipInputStream(new FileInputStream(tmpZip));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(target));
        for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
            out.putNextEntry(ze);
            for(int read = zin.read(buffer); read > -1; read = zin.read(buffer)){
                out.write(buffer, 0, read);
            }
            out.closeEntry();
        }

        InputStream in = Files.newInputStream(resourcePath);
        out.putNextEntry(createZipEntry(resourcePath, "/BOOT-INF/lib/"));
        for(int read = in.read(buffer); read > -1; read = in.read(buffer)){
            out.write(buffer, 0, read);
        }
        out.closeEntry();

        in.close();
        out.close();
        tmpZip.delete();
    }

    private ZipEntry createZipEntry(Path file, String path) throws IOException {
        ZipEntry entry = new ZipEntry(path + file.getFileName().toString());

        byte[] buffer = new byte[8192];
        int bytesRead = -1;
        InputStream is = Files.newInputStream(file);
        CRC32 crc = new CRC32();
        int size = 0;
        while ((bytesRead = is.read(buffer)) != -1) {
            crc.update(buffer, 0, bytesRead);
            size += bytesRead;
        }
        entry.setSize(size);
        entry.setCompressedSize(size);
        entry.setCrc(crc.getValue());
        entry.setMethod(ZipEntry.STORED);
        return entry;
    }

    private void addSecretTokenToApplicationProperties() throws MojoExecutionException {
        String newToken = UUID.randomUUID().toString();
        log.verbose("Generating the spring devtools token in property: " + DEV_TOOLS_REMOTE_SECRET);

        // We always add to application.properties, even when an application.yml exists, since both
        // files are evaluated by Spring Boot.
        File file = new File(getProject().getBasedir(), "target/classes/application.properties");
        file.getParentFile().mkdirs();
        String text = String.format("%s" +
                                    "# Remote secret added by fabric8-maven-plugin\n" +
                                    "%s=%s\n",
                                    file.exists() ? "\n" : "", DEV_TOOLS_REMOTE_SECRET, newToken);

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.append(text);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to append to file: " + file + ". " + e, e);
        }
    }

    private boolean isSpringBootRepackage() {
        MavenProject project = getProject();
        Plugin plugin = project.getPlugin(SPRING_BOOT_MAVEN_PLUGIN_GA);
        if (plugin != null) {
            Map<String, PluginExecution> executionsAsMap = plugin.getExecutionsAsMap();
            if (executionsAsMap != null) {
                for (PluginExecution execution : executionsAsMap.values()) {
                    List<String> goals = execution.getGoals();
                    if (goals.contains("repackage")) {
                        log.verbose("Using fat jar packaging as the spring boot plugin is using `repackage` goal execution");
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
