package io.fabric8.maven.watcher.standard;

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
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.service.PodLogService;
import io.fabric8.maven.core.service.PortForwardService;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.IoUtil;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.core.util.SpringBootProperties;
import io.fabric8.maven.core.util.SpringBootUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.watcher.api.BaseWatcher;
import io.fabric8.maven.watcher.api.WatcherContext;
import io.fabric8.utils.Closeables;
import io.fabric8.utils.PropertiesHelper;
import io.fabric8.utils.Strings;

import org.apache.maven.project.MavenProject;

import static io.fabric8.maven.core.util.SpringBootProperties.DEV_TOOLS_REMOTE_SECRET;

public class SpringBootWatcher extends BaseWatcher {

    private static final String SPRING_BOOT_MAVEN_PLUGIN_GA = "org.springframework.boot:spring-boot-maven-plugin";

    private static final int DEFAULT_SERVER_PORT = 8080;

    // Available configuration keys
    private enum Config implements Configs.Key {

        // The time to wait for the service to be exposed (by the expose controller)
        serviceUrlWaitTimeSeconds {{ d = "5"; }};

        public String def() { return d; } protected String d;
    }

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

        String url = getServiceExposeUrl(kubernetes, resources);
        if (url == null) {
            url = getPortForwardUrl(resources);
        }

        if (url != null) {
            runRemoteSpringApplication(url);
        } else {
            throw new IllegalStateException("Unable to open a channel to the remote pod.");
        }
    }

    private String getPortForwardUrl(final Set<HasMetadata> resources) throws Exception {
        LabelSelector selector = KubernetesResourceUtil.getPodLabelSelector(resources);
        if (selector == null) {
            log.warn("Unable to determine a selector for application pods");
            return null;
        }

        Properties properties = SpringBootUtil.getSpringBootApplicationProperties(getContext().getProject());

        PortForwardService portForwardService = getContext().getFabric8ServiceHub().getPortForwardService();
        int port = IoUtil.getFreeRandomPort();
        int containerPort = findSpringBootWebPort(properties);
        portForwardService.forwardPortAsync(getContext().getLogger(), selector, containerPort, port);
        return createForwardUrl(properties, port);
    }

    private int findSpringBootWebPort(Properties properties) {
        return PropertiesHelper.getInteger(properties, SpringBootProperties.SERVER_PORT, DEFAULT_SERVER_PORT);
    }

    private String createForwardUrl(Properties properties, int localPort) {
        String scheme = Strings.isNotBlank(properties.getProperty(SpringBootProperties.SERVER_KEYSTORE)) ? "https://" : "http://";
        String contextPath = properties.getProperty(SpringBootProperties.SERVER_CONTEXT_PATH, "");
        return scheme + "localhost:" + localPort + contextPath;
    }

    private String getServiceExposeUrl(KubernetesClient kubernetes, Set<HasMetadata> resources) throws InterruptedException {
        long serviceUrlWaitTimeSeconds = Configs.asInt(getConfig(Config.serviceUrlWaitTimeSeconds));
        for (HasMetadata entity : resources) {
            if (entity instanceof Service) {
                Service service = (Service) entity;
                String name = KubernetesHelper.getName(service);
                Resource<Service, DoneableService> serviceResource = kubernetes.services().inNamespace(getContext().getNamespace()).withName(name);
                String url = null;
                // lets wait a little while until there is a service URL in case the exposecontroller is running slow
                for (int i = 0; i < serviceUrlWaitTimeSeconds; i++) {
                    if (i > 0) {
                        Thread.sleep(1000);
                    }
                    Service s = serviceResource.get();
                    if (s != null) {
                        url = KubernetesHelper.getOrCreateAnnotations(s).get(Annotations.Service.EXPOSE_URL);
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
                    return url;
                }
            }
        }

        log.info("No exposed service found for connecting the dev tools");
        return null;
    }

    private boolean isExposeService(Service service) {
        String expose = KubernetesHelper.getLabels(service).get("expose");
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
            // Add dev tools to the classpath (the main class is not read from BOOT-INF/lib)
            try {
                File devtools = getSpringBootDevToolsJar(getContext().getProject());
                buffer.append(File.pathSeparator);
                buffer.append(devtools.getCanonicalPath());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to include devtools in the classpath: " + e, e);
            }

            buffer.append(" -Dspring.devtools.remote.secret=");
            buffer.append(remoteSecret);
            buffer.append(" org.springframework.boot.devtools.RemoteSpringApplication ");
            buffer.append(url);

            try {
                String command = buffer.toString();
                log.debug("Running: " + command);
                final Process process = Runtime.getRuntime().exec(command);

                final AtomicBoolean outputEnabled = new AtomicBoolean(true);
                Runtime.getRuntime().addShutdownHook(new Thread("fabric8:watch [spring-boot] shutdown hook") {
                    @Override
                    public void run() {
                        log.info("Terminating the Spring remote client...");
                        outputEnabled.set(false);
                        process.destroy();
                    }
                });
                Logger logger = new PrefixedLogger("Spring-Remote", log);
                Thread stdOutPrinter = startOutputProcessor(logger, process.getInputStream(), false, outputEnabled);
                Thread stdErrPrinter = startOutputProcessor(logger, process.getErrorStream(), true, outputEnabled);
                int status = process.waitFor();
                stdOutPrinter.join();
                stdErrPrinter.join();
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

    protected Thread startOutputProcessor(final Logger logger, final InputStream inputStream, final boolean error, final AtomicBoolean outputEnabled) throws IOException {
        Thread printer = new Thread() {
            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (outputEnabled.get()) {
                            if (error) {
                                logger.error("%s", line);
                            } else {
                                logger.info("%s", line);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (outputEnabled.get()) {
                        logger.error("Failed to process " + (error ? "stderr" : "stdout") + " from spring-remote process: " + e);
                    }
                } finally {
                    Closeables.closeQuietly(reader);
                }
            }
        };

        printer.start();
        return printer;
    }

    private File getSpringBootDevToolsJar(MavenProject project) throws IOException {
        String version = SpringBootUtil.getSpringBootDevToolsVersion(project);
        if (version == null) {
            throw new IllegalStateException("Unable to find the spring-boot version");
        }
        return getContext().getFabric8ServiceHub().getArtifactResolverService().resolveArtifact(SpringBootProperties.SPRING_BOOT_GROUP_ID, SpringBootProperties.SPRING_BOOT_DEVTOOLS_ARTIFACT_ID, version, "jar");
    }

}
