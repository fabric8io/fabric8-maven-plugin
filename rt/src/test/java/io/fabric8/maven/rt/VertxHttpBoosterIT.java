package io.fabric8.maven.rt;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.openshift.api.model.Route;
import okhttp3.Response;
import org.eclipse.jgit.lib.Repository;
import org.json.JSONObject;
import org.testng.annotations.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class VertxHttpBoosterIT extends Core {
    public static final String SPRING_BOOT_HTTP_BOOSTER_GIT = "https://github.com/openshiftio-vertx-boosters/vertx-http-booster.git";

    public final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    private final String testEndpoint = "/api/greeting";

    public static final String ANNOTATION_KEY = "vertx-testKey", ANNOTATION_VALUE = "vertx-testValue";

    public final String FMP_CONFIGURATION_FILE = "/fmp-plugin-config.xml";

    @Test
    public void deploy_springboot_app_once() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH);

        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
    }

    @Test
    public void redeploy_springboot_app() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH);
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);

        // change the source code
        updateSourceCode(testRepository, RELATIVE_POM_PATH);
        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, ANNOTATION_KEY, ANNOTATION_VALUE, FMP_CONFIGURATION_FILE);

        // redeploy and assert
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        assert checkDeploymentsForAnnotation(ANNOTATION_KEY);
    }

    public void deployAndAssert(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        String sampleProjectArtifactId = readPomModelFromFile(new File(testRepository.getWorkTree().getAbsolutePath(), RELATIVE_POM_PATH)).getArtifactId();
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
        waitTillApplicationPodStarts();

        assertThat(openShiftClient).deployment(sampleProjectArtifactId);
        assertThat(openShiftClient).service(sampleProjectArtifactId);

        RouteAssert.assertRoute(openShiftClient, sampleProjectArtifactId);
        assertThatWeServeAsExpected(getApplicationRouteWithName(TESTSUITE_REPOSITORY_ARTIFACT_ID));
    }

    private void assertThatWeServeAsExpected(Route applicationRoute) throws Exception {
        String hostUrl = "http://" + applicationRoute.getSpec().getHost() + testEndpoint;

        Response readResponse = makeHttpRequest(HttpRequestType.GET, hostUrl, null);
        assert new JSONObject(readResponse.body().string()).getString("content").equals("Hello, World!");

        // let's change default greeting message
        hostUrl += "?name=vertx";

        readResponse = makeHttpRequest(HttpRequestType.GET, hostUrl, null);
        assert new JSONObject(readResponse.body().string()).getString("content").equals("Hello, vertx!");
    }
}
