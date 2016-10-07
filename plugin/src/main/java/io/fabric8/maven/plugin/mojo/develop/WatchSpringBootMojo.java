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

package io.fabric8.maven.plugin.mojo.develop;

import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ClientResource;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.SpringBootUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Closeables;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.Set;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.maven.core.util.SpringBootProperties.DEV_TOOLS_REMOTE_SECRET;

/**
 * Runs the remote spring boot application
 */
@Mojo(name = "watch-spring-boot", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.VALIDATE)
@Execute(goal = "deploy")
public class WatchSpringBootMojo extends AbstractTailLogMojo {

    @Override
    protected void applyEntities(Controller controller, final KubernetesClient kubernetes, final String namespace, String fileName, final Set<HasMetadata> entities) throws Exception {

        tailAppPodsLogs(kubernetes, namespace, entities, false, null, true, null, false);

        boolean serviceFound = false;
        long serviceUrlWaitTimeSeconds = this.serviceUrlWaitTimeSeconds;
        for (HasMetadata entity : entities) {
            if (entity instanceof Service) {
                Service service = (Service) entity;
                String name = getName(service);
                ClientResource<Service, DoneableService> serviceResource = kubernetes.services().inNamespace(namespace).withName(name);
                String url = null;
                // lets wait a little while until there is a service URL in case the exposecontroller is running slow
                for (int i = 0; i < serviceUrlWaitTimeSeconds; i++) {
                    if (i > 0) {
                        Thread.sleep(1000);
                    }
                    Service s = serviceResource.get();
                    if (s != null) {
                        url = getExternalServiceURL(s);
                        if (Strings.isNotBlank(url)) {
                            break;
                        }
                    }
                    if (!isExposeService(service)) {
                        break;
                    }
                }

                // lets not wait for other services
                serviceUrlWaitTimeSeconds = 1;
                if (Strings.isNotBlank(url) && url.startsWith("http")) {
                    serviceFound = true;
                    runRemoteSpringApplication(url);
                }
            }
        }
        if (!serviceFound) {
            throw new MojoExecutionException("No external service found for this application! So cannot watch a remote container!");
        }
    }

    private void runRemoteSpringApplication(String url) throws MojoExecutionException {
        log.info("Running RemoteSpringApplication against endpoint: " + url);

        Properties properties = SpringBootUtil.getSpringBootApplicationProperties(project);
        String remoteSecret = properties.getProperty(DEV_TOOLS_REMOTE_SECRET, System.getProperty(DEV_TOOLS_REMOTE_SECRET));
        if (Strings.isNullOrBlank(remoteSecret)) {
            log.warn("There is no `" + DEV_TOOLS_REMOTE_SECRET + "` property defined in your src/main/resources/application.properties. Please add one!");
            throw new MojoExecutionException("No " + DEV_TOOLS_REMOTE_SECRET + " property defined in application.properties or system properties");
        }

        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader pluginClassLoader = (URLClassLoader) classLoader;
            URLClassLoader projectClassLoader = ClassUtil.createProjectClassLoader(project, log);
            URLClassLoader[] classLoaders = {projectClassLoader, pluginClassLoader};

            StringBuilder buffer = new StringBuilder("java -cp ");
            int count = 0;
            for (URLClassLoader urlClassLoader : classLoaders) {
                URL[] urLs = urlClassLoader.getURLs();
                for (URL u : urLs) {
                    if (count++ > 0) {
                        buffer.append(File.pathSeparator);
                    }
                    try {
                        URI uri = u.toURI();
                        File file = new File(uri);
                        buffer.append(file.getCanonicalPath());
                    } catch (Exception e) {
                        throw new MojoExecutionException("Failed to create classpath: " + e, e);
                    }
                }
            }
            buffer.append(" -Dspring.devtools.remote.secret=");
            buffer.append(remoteSecret);
            buffer.append(" org.springframework.boot.devtools.RemoteSpringApplication ");
            buffer.append(url);

            try {
                String command = buffer.toString();
                log.debug("Running: " + command);
                final Process process = Runtime.getRuntime().exec(command);

                Runtime.getRuntime().addShutdownHook(new Thread("mvn fabric8:watch-spring-boot shutdown hook") {
                    @Override
                    public void run() {
                        log.info("Terminating the RemoteSpringApplication");
                        process.destroy();
                    }
                });
                Logger logger = createLogger("[[G]][Spring][[G]] ");
                processOutput(logger, process.getInputStream(), false);
                processOutput(logger, process.getErrorStream(), true);
                int status = process.waitFor();
                if (status != 0) {
                    log.warn("Process returned status: " + status);
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to run RemoteSpringApplication: " + e, e);
            }

        } else {
            throw new MojoExecutionException("ClassLoader must be a URLClassLoader but it is: " + classLoader.getClass().getName());
        }
    }


    protected void processOutput(Logger logger, InputStream inputStream, boolean error) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            while (true) {
                String line = reader.readLine();
                if (line == null) break;

                if (error) {
                    logger.error("%s", line);
                } else {
                    logger.info("%s", line);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to process " + (error ? "stderr" : "stdout") + ": " + e);
            throw e;
        } finally {
            Closeables.closeQuietly(reader);
        }
    }

}
