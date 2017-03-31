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
package io.fabric8.maven.core.service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.service.kubernetes.DockerBuildService;
import io.fabric8.maven.core.service.openshift.OpenshiftBuildService;
import io.fabric8.maven.core.util.LazyBuilder;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.client.OpenShiftClient;

import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;

/**
 * @author nicola
 * @since 17/02/2017
 */
public class Fabric8ServiceHub {

    /*
     * Configured resources
     */
    private ClusterAccess clusterAccess;

    private PlatformMode platformMode;

    private Logger log;

    private ServiceHub dockerServiceHub;

    private BuildService.BuildServiceConfig buildServiceConfig;

    private RepositorySystem repositorySystem;

    private MavenProject mavenProject;

    /**
     * Configurable with default
     */
    private Controller controller;

    /*
     * Computed resources
     */

    private PlatformMode resolvedMode;

    private KubernetesClient client;

    private ConcurrentHashMap<Class<?>, LazyBuilder<?>> services = new ConcurrentHashMap<>();

    private Fabric8ServiceHub() {
    }

    private void init() {
        Objects.requireNonNull(clusterAccess, "clusterAccess");
        Objects.requireNonNull(log, "log");

        this.resolvedMode = clusterAccess.resolvePlatformMode(platformMode, log);
        if (resolvedMode != PlatformMode.kubernetes && resolvedMode != PlatformMode.openshift) {
            throw new IllegalArgumentException("Unknown platform mode " + platformMode + " resolved as "+ resolvedMode);
        }
        this.client = clusterAccess.createDefaultClient(log);

        if (this.controller == null) {
            this.controller = new Controller(this.client);
            this.controller.setThrowExceptionOnError(true);
        }
    }

    public ClientToolsService getClientToolsService() {
        this.services.putIfAbsent(ClientToolsService.class, new LazyBuilder<ClientToolsService>() {
            @Override
            protected ClientToolsService build() {
                return new ClientToolsService(controller, log);
            }
        });
        return (ClientToolsService) this.services.get(ClientToolsService.class).get();
    }

    public PortForwardService getPortForwardService() {
        this.services.putIfAbsent(PortForwardService.class, new LazyBuilder<PortForwardService>() {
            @Override
            protected PortForwardService build() {
                return new PortForwardService(getClientToolsService(), log, client);
            }
        });
        return (PortForwardService) this.services.get(PortForwardService.class).get();
    }

    public BuildService getBuildService() {
        this.services.putIfAbsent(BuildService.class, new LazyBuilder<BuildService>() {
            @Override
            protected BuildService build() {
                BuildService buildService;
                // Creating platform-dependent services
                if (resolvedMode == PlatformMode.openshift) {
                    // Openshift services
                    buildService = new OpenshiftBuildService((OpenShiftClient) client, log, dockerServiceHub, buildServiceConfig);
                } else {
                    // Kubernetes services
                    buildService = new DockerBuildService(dockerServiceHub, buildServiceConfig);
                }
                return buildService;
            }
        });
        return (BuildService) this.services.get(BuildService.class).get();
    }

    public ArtifactResolverService getArtifactResolverService() {
        this.services.putIfAbsent(ArtifactResolverService.class, new LazyBuilder<ArtifactResolverService>() {
            @Override
            protected ArtifactResolverService build() {
                return new ArtifactResolverServiceMavenImpl(repositorySystem, mavenProject);
            }
        });
        return (ArtifactResolverService) this.services.get(ArtifactResolverService.class).get();
    }

    // =================================================

    public static class Builder {

        private Fabric8ServiceHub hub;

        public Builder() {
            this.hub = new Fabric8ServiceHub();
        }

        public Builder clusterAccess(ClusterAccess clusterAccess) {
            hub.clusterAccess = clusterAccess;
            return this;
        }

        public Builder platformMode(PlatformMode platformMode) {
            hub.platformMode = platformMode;
            return this;
        }

        public Builder log(Logger log) {
            hub.log = log;
            return this;
        }

        public Builder dockerServiceHub(ServiceHub dockerServiceHub) {
            hub.dockerServiceHub = dockerServiceHub;
            return this;
        }

        public Builder buildServiceConfig(BuildService.BuildServiceConfig buildServiceConfig) {
            hub.buildServiceConfig = buildServiceConfig;
            return this;
        }

        public Builder controller(Controller controller) {
            hub.controller = controller;
            return this;
        }

        public Builder repositorySystem(RepositorySystem repositorySystem) {
            hub.repositorySystem = repositorySystem;
            return this;
        }

        public Builder mavenProject(MavenProject mavenProject) {
            hub.mavenProject = mavenProject;
            return this;
        }

        public Fabric8ServiceHub build() {
            hub.init();
            return hub;
        }
    }
}
