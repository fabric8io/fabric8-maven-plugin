package io.fabric8.maven.core.service.openshift;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moved from 'old' Kubernetes Helper. This client should be only in use temporarily as it is
 * supposed to be able to use the OpenShiftClient directly for Jenkinshift transparently.
 *
 * This client is only used for creating / updating BuildConfigs with ApplyService
 * @author roland
 * @since 23.05.17
 */
public class JenkinShiftClient extends DefaultOpenShiftClient {

    public JenkinShiftClient(String jenkinshiftUrl) throws KubernetesClientException {
        super(new ConfigBuilder().withMasterUrl(jenkinshiftUrl).build());
        this.httpClient = createHttpClient(getConfiguration());
    }

    // TODO until jenkinshift supports HTTPS lets disable HTTPS by default
    private OkHttpClient createHttpClient(final Config config) {
        try {
            OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();

            // Follow any redirects
            httpClientBuilder.followRedirects(true);
            httpClientBuilder.followSslRedirects(true);

            if (config.isTrustCerts()) {
                    httpClientBuilder.hostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String s, SSLSession sslSession) {
                            return true;
                        }
                    });
                }

                if (StringUtils.isNotBlank(config.getUsername()) && StringUtils.isNotBlank(config.getPassword())) {
                    httpClientBuilder.addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request authReq = chain.request().newBuilder().addHeader("Authorization", Credentials.basic(config.getUsername(), config.getPassword())).build();
                            return chain.proceed(authReq);
                        }
                    });
                } else if (config.getOauthToken() != null) {
                    httpClientBuilder.addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request authReq = chain.request().newBuilder().addHeader("Authorization", "Bearer " + config.getOauthToken()).build();
                            return chain.proceed(authReq);
                        }
                    });
                }

                Logger reqLogger = LoggerFactory.getLogger(HttpLoggingInterceptor.class);
                if (reqLogger.isTraceEnabled()) {
                    HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
                    loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
                    httpClientBuilder.addNetworkInterceptor(loggingInterceptor);
                }

                if (config.getConnectionTimeout() > 0) {
                    httpClientBuilder.connectTimeout(config.getConnectionTimeout(), TimeUnit.MILLISECONDS);
                }

                if (config.getRequestTimeout() > 0) {
                    httpClientBuilder.readTimeout(config.getRequestTimeout(), TimeUnit.MILLISECONDS);
                }

                // Only check proxy if it's a full URL with protocol
/*
                        if (config.getMasterUrl().toLowerCase().startsWith(Config.HTTP_PROTOCOL_PREFIX) || config.getMasterUrl().startsWith(Config.HTTPS_PROTOCOL_PREFIX)) {
                            try {
                                URL proxyUrl = getProxyUrl(config);
                                if (proxyUrl != null) {
                                    httpClientBuilder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUrl.getHost(), proxyUrl.getPort())));
                                }
                            } catch (MalformedURLException e) {
                                throw new KubernetesClientException("Invalid proxy server configuration", e);
                            }
                        }
*/

                if (config.getUserAgent() != null && !config.getUserAgent().isEmpty()) {
                    httpClientBuilder.addNetworkInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request agent = chain.request().newBuilder().header("User-Agent", config.getUserAgent()).build();
                            return chain.proceed(agent);
                        }
                    });
                }
                return httpClientBuilder.build();
            } catch (Exception e) {
                throw KubernetesClientException.launderThrowable(e);
            }
        }
}
