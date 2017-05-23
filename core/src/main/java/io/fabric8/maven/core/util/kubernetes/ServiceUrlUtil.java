package io.fabric8.maven.core.util.kubernetes;

import java.util.List;

import com.google.common.base.Objects;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.LoadBalancerStatus;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeSpec;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressRuleValue;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBackend;
import io.fabric8.kubernetes.api.model.extensions.IngressList;
import io.fabric8.kubernetes.api.model.extensions.IngressRule;
import io.fabric8.kubernetes.api.model.extensions.IngressSpec;
import io.fabric8.kubernetes.api.model.extensions.IngressTLS;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.lang3.StringUtils;

/**
 * @author roland
 * @since 23.05.17
 */
public class ServiceUrlUtil {

    private static final String DEFAULT_PROTO = "tcp";
    private static final String HOST_SUFFIX = "_SERVICE_HOST";
    private static final String PORT_SUFFIX = "_SERVICE_PORT";
    private static final String PROTO_SUFFIX = "_TCP_PROTO";

    /**
     * Returns the URL to access the service; using the environment variables, routes
     * or service clusterIP address
     *
     * @throws IllegalArgumentException if the URL cannot be found for the serviceName and namespace
     */
    public static String getServiceURL(KubernetesClient client, String serviceName, String serviceNamespace, String serviceProtocol, boolean serviceExternal) {
        Service srv = null;
        String serviceHost = serviceToHostOrBlank(serviceName);
        String servicePort = serviceToPortOrBlank(serviceName);
        String serviceProto = serviceProtocol != null ? serviceProtocol : serviceToProtocol(serviceName, servicePort);

        //Use specified or fallback namespace.
        String actualNamespace = StringUtils.isNotBlank(serviceNamespace) ? serviceNamespace : client.getNamespace();

        //1. Inside Kubernetes: Services as ENV vars
        if (!serviceExternal && StringUtils.isNotBlank(serviceHost) && StringUtils.isNotBlank(servicePort) && StringUtils.isNotBlank(serviceProtocol)) {
            return serviceProtocol + "://" + serviceHost + ":" + servicePort;
            //2. Anywhere: When namespace is passed System / Env var. Mostly needed for integration tests.
        } else if (StringUtils.isNotBlank(actualNamespace)) {
            srv = client.services().inNamespace(actualNamespace).withName(serviceName).get();
        }

        if (srv == null) {
            // lets try use environment variables
            String hostAndPort = getServiceHostAndPort(serviceName, "", "");
            if (!hostAndPort.startsWith(":")) {
                return serviceProto + "://" + hostAndPort;
            }
        }
        if (srv == null) {
            throw new IllegalArgumentException("No kubernetes service could be found for name: " + serviceName + " in namespace: " + actualNamespace);
        }

        String answer = KubernetesHelper.getOrCreateAnnotations(srv).get(Fabric8Annotations.SERVICE_EXPOSE_URL.toString());
        if (StringUtils.isNotBlank(answer)) {
            return answer;
        }

        if (OpenshiftHelper.isOpenShift(client)) {
            OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);
            Route route = openShiftClient.routes().inNamespace(actualNamespace).withName(serviceName).get();
            if (route != null) {
                return (serviceProto + "://" + route.getSpec().getHost()).toLowerCase();
            }
        }

        ServicePort port = findServicePortByName(srv, null);
        if (port == null) {
            throw new RuntimeException("Couldn't find port: " + null + " for service:" + serviceName);
        }

        String clusterIP = srv.getSpec().getClusterIP();
        if ("None".equals(clusterIP)) {
            throw new IllegalStateException("Service: " + serviceName + " in namespace:" + serviceNamespace + "is head-less. Search for endpoints instead.");
        }

        Integer portNumber = port.getPort();
        if (StringUtils.isBlank(clusterIP)) {
            IngressList ingresses = client.extensions().ingresses().inNamespace(serviceNamespace).list();
            if (ingresses != null) {
                List<Ingress> items = ingresses.getItems();
                if (items != null) {
                    for (Ingress item : items) {
                        String ns = KubernetesHelper.getNamespace(item);
                        if (Objects.equal(serviceNamespace, ns)) {
                            IngressSpec spec = item.getSpec();
                            if (spec != null) {
                                List<IngressRule> rules = spec.getRules();
                                List<IngressTLS> tls = spec.getTls();
                                if (rules != null) {
                                    for (IngressRule rule : rules) {
                                        HTTPIngressRuleValue http = rule.getHttp();
                                        if (http != null) {
                                            List<HTTPIngressPath> paths = http.getPaths();
                                            if (paths != null) {
                                                for (HTTPIngressPath path : paths) {
                                                    IngressBackend backend = path.getBackend();
                                                    if (backend != null) {
                                                        String backendServiceName = backend.getServiceName();
                                                        if (serviceName.equals(backendServiceName) && portsMatch(port, backend.getServicePort())) {
                                                            String pathPostfix = path.getPath();
                                                            if (tls != null) {
                                                                for (IngressTLS tlsHost : tls) {
                                                                    List<String> hosts = tlsHost.getHosts();
                                                                    if (hosts != null) {
                                                                        for (String host : hosts) {
                                                                            if (StringUtils.isNotBlank(host)) {
                                                                                return String.format("https://%s/%s", host, preparePath(pathPostfix));
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            answer = rule.getHost();
                                                            if (StringUtils.isNotBlank(answer)) {
                                                                return String.format("http://%s/%s",answer, preparePath(pathPostfix));
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // lets try use the status on GKE
            ServiceStatus status = srv.getStatus();
            if (status != null) {
                LoadBalancerStatus loadBalancerStatus = status.getLoadBalancer();
                if (loadBalancerStatus != null) {
                    List<LoadBalancerIngress> loadBalancerIngresses = loadBalancerStatus.getIngress();
                    if (loadBalancerIngresses != null) {
                        for (LoadBalancerIngress loadBalancerIngress : loadBalancerIngresses) {
                            String ip = loadBalancerIngress.getIp();
                            if (StringUtils.isNotBlank(ip)) {
                                clusterIP = ip;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (StringUtils.isBlank(clusterIP)) {
            // on vanilla kubernetes we can use nodePort to access things externally
            boolean found = false;
            Integer nodePort = port.getNodePort();
            if (nodePort != null) {
                NodeList nodeList = client.nodes().list();
                if (nodeList != null) {
                    List<Node> items = nodeList.getItems();
                    if (items != null) {
                        for (Node item : items) {
                            NodeStatus status = item.getStatus();
                            if (!found && status != null) {
                                List<NodeAddress> addresses = status.getAddresses();
                                if (addresses != null) {
                                    for (NodeAddress address : addresses) {
                                        String ip = address.getAddress();
                                        if (StringUtils.isNotBlank(ip)) {
                                            clusterIP = ip;
                                            portNumber = nodePort;
                                            found = true;
                                            break;
                                        }

                                    }

                                }
                            }
                            if (!found) {
                                NodeSpec spec = item.getSpec();
                                if (spec != null) {
                                    clusterIP = spec.getExternalID();
                                    if (StringUtils.isNotBlank(clusterIP)) {
                                        portNumber = nodePort;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return (serviceProto + "://" + clusterIP + ":" + portNumber).toLowerCase();
    }

    /**
     * Returns true if the given servicePort matches the intOrString value
     */
    private static boolean portsMatch(ServicePort servicePort, IntOrString intOrString) {
        if (intOrString != null) {
            Integer port = servicePort.getPort();
            Integer intVal = intOrString.getIntVal();
            String strVal = intOrString.getStrVal();
            if (intVal != null) {
                if (port != null) {
                    return port.intValue() == intVal.intValue();
                } else {
                    /// should we find the port by name now?
                }
            } else if (strVal != null ){
                return Objects.equal(strVal, servicePort.getName());
            }
        }
        return false;
    }


    private static String preparePath(String path) {
        if (StringUtils.isBlank(path)) {
            return "";
        }
        String ret = path;
        while (ret.startsWith("/")) {
            ret = ret.substring(1);
        }
        return ret;
    }

    private static ServicePort findServicePortByName(Service service, String portName) {
        if (StringUtils.isBlank(portName)) {
            return service.getSpec().getPorts().iterator().next();
        }

        for (ServicePort servicePort : service.getSpec().getPorts()) {
            if (Objects.equal(servicePort.getName(), portName)) {
                return servicePort;
            }
        }
        return null;
    }


    /**
     * Returns the service host name or a blank string if it could not be resolved
     */
    private static String serviceToHostOrBlank(String serviceName) {
        return getEnvVarOrSystemProperty(toServiceHostEnvironmentVariable(serviceName), "");
    }

    private static String serviceToProtocol(String serviceName, String servicePort) {
        return getEnvVarOrSystemProperty(toEnvVariable(serviceName + PORT_SUFFIX + "_" + servicePort + PROTO_SUFFIX), DEFAULT_PROTO);
    }

    /**
     * Returns the named port for the given service name or blank
     */
    private static String serviceToPortOrBlank(String serviceName) {
        String envVarName = toServicePortEnvironmentVariable(serviceName);
        return getEnvVarOrSystemProperty(envVarName, "");
    }

        /**
     * Returns the kubernetes environment variable name for the service host for the given service name
     */
    private static String toServiceHostEnvironmentVariable(String serviceName) {
        return toEnvVariable(serviceName + HOST_SUFFIX);
    }

    private static String toServicePortEnvironmentVariable(String serviceName) {
        String name = serviceName + PORT_SUFFIX;
        return toEnvVariable(name);
    }

    private static String toEnvVariable(String serviceName) {
        return serviceName.toUpperCase().replaceAll("-", "_");
    }

        /**
     * Returns the service host and port for the given environment variable name.
     *
     * @param serviceName the name of the service which is used as a prefix to access the <code>${serviceName}_SERVICE_HOST</code> and <code>${serviceName}_SERVICE_PORT</code> environment variables to find the hos and port
     * @param defaultHost the default host to use if not injected via an environment variable (e.g. localhost)
     * @parma defaultPort the default port to use to connect to the service if there is not an environment variable defined
     */
    private static String getServiceHostAndPort(String serviceName, String defaultHost, String defaultPort) {
        String serviceEnvVarPrefix = getServiceEnvVarPrefix(serviceName);
        String hostEnvVar = serviceEnvVarPrefix + "_HOST";
        String portEnvVar = serviceEnvVarPrefix + "_PORT";

        String host = getEnvVarOrSystemProperty(hostEnvVar, hostEnvVar, defaultHost);
        String port = getEnvVarOrSystemProperty(portEnvVar, portEnvVar, defaultPort);

        String answer = host + ":" + port;
        return answer;
    }

    private static String getServiceEnvVarPrefix(String serviceName) {
        return serviceName.toUpperCase().replace('-', '_') + "_SERVICE";
    }

    private static String getEnvVarOrSystemProperty(String envVarName, String systemProperty, String defaultValue) {
        String answer = null;
        answer = System.getenv(envVarName);
        if (StringUtils.isBlank(answer)) {
            answer = System.getProperty(systemProperty, defaultValue);
        }
        if (StringUtils.isNotBlank(answer)) {
            return answer;
        } else {
            return defaultValue;
        }
    }

    private static String getEnvVarOrSystemProperty(String envVarName, String defaultValue) {
        return getEnvVarOrSystemProperty(envVarName,envVarName,defaultValue);
    }
}
