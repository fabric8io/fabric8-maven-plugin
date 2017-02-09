package io.fabric8.maven.plugin.watcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ClientResource;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.service.PodLogService;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.core.util.SpringBootUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Closeables;
import io.fabric8.utils.Strings;

import static io.fabric8.kubernetes.api.KubernetesHelper.getLabels;
import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateAnnotations;
import static io.fabric8.maven.core.util.SpringBootProperties.DEV_TOOLS_REMOTE_SECRET;

public class SpringBootWatcher extends BaseWatcher {

    private static final String SPRING_BOOT_MAVEN_PLUGIN_GA = "org.springframework.boot:spring-boot-maven-plugin";

    private long serviceUrlWaitTimeSeconds = 5;

    public SpringBootWatcher(WatcherContext watcherContext) {
        super(watcherContext, "spring-boot");
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs, Set<HasMetadata> resources, PlatformMode mode) {
        return MavenUtil.hasPlugin(getContext().getProject(), SPRING_BOOT_MAVEN_PLUGIN_GA);
    }

    @Override
    public void watch(List<ImageConfiguration> configs, Set<HasMetadata> resources, PlatformMode mode) throws Exception {
        KubernetesClient kubernetes = getContext().getKubernetesClient();

        PodLogService.PodLogServiceContext logContext = new PodLogService.PodLogServiceContext.Builder()
                .log(log)
                .newPodLog(getContext().getNewPodLogger())
                .oldPodLog(getContext().getOldPodLogger())
                .build();

        new PodLogService(logContext).tailAppPodsLogs(kubernetes, getContext().getNamespace(), resources, false, null, true, null, false);

        long serviceUrlWaitTimeSeconds = this.serviceUrlWaitTimeSeconds;
        boolean serviceFound = false;
        for (HasMetadata entity : resources) {
            if (entity instanceof Service) {
                Service service = (Service) entity;
                String name = KubernetesHelper.getName(service);
                ClientResource<Service, DoneableService> serviceResource = kubernetes.services().inNamespace(getContext().getNamespace()).withName(name);
                String url = null;
                // lets wait a little while until there is a service URL in case the exposecontroller is running slow
                for (int i = 0; i < serviceUrlWaitTimeSeconds; i++) {
                    if (i > 0) {
                        Thread.sleep(1000);
                    }
                    Service s = serviceResource.get();
                    if (s != null) {
                        url = getOrCreateAnnotations(s).get(Annotations.Service.EXPOSE_URL);
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
            throw new IllegalStateException("No external service found for this application! So cannot watch a remote container!");
        }
    }

    private boolean isExposeService(Service service) {
        String expose = getLabels(service).get("expose");
        return expose != null && expose.toLowerCase().equals("true");
    }

    private void runRemoteSpringApplication(String url) {
        log.info("Running RemoteSpringApplication against endpoint: " + url);

        Properties properties = SpringBootUtil.getSpringBootApplicationProperties(getContext().getProject());
        String remoteSecret = properties.getProperty(DEV_TOOLS_REMOTE_SECRET, System.getProperty(DEV_TOOLS_REMOTE_SECRET));
        if (Strings.isNullOrBlank(remoteSecret)) {
            log.warn("There is no `%s` property defined in your src/main/resources/application.properties. Please add one!", DEV_TOOLS_REMOTE_SECRET);
            throw new IllegalStateException("No " + DEV_TOOLS_REMOTE_SECRET + " property defined in application.properties or system properties");
        }

        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader pluginClassLoader = (URLClassLoader) classLoader;
            URLClassLoader projectClassLoader = ClassUtil.createProjectClassLoader(getContext().getProject(), log);
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
                        throw new IllegalStateException("Failed to create classpath: " + e, e);
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
                Logger logger = new PrefixedLogger("Spring-Remote", log);
                processOutput(logger, process.getInputStream(), false);
                processOutput(logger, process.getErrorStream(), true);
                int status = process.waitFor();
                if (status != 0) {
                    log.warn("Process returned status: %s", status);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to run RemoteSpringApplication: " + e, e);
            }

        } else {
            throw new IllegalStateException("ClassLoader must be a URLClassLoader but it is: " + classLoader.getClass().getName());
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
