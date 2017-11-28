package jar;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.testng.annotations.Test;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;


/**
 * Created by hshinde on 11/23/17.
 */

public class SpringbootHttpBoosterIT extends Core {

    public static final String SPRING_BOOT_HTTP_BOOSTER_GIT = "https://github.com/snowdrop/spring-boot-http-booster.git";

    @Test
    public void deploy_springboot_app_once() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT);

        deployAndAssert(testRepository);
    }

    @Test
    public void redeploy_springboot_app() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT);
        deployAndAssert(testRepository);

        // change the source code
        updateSourceCode(testRepository);

        // redeploy and assert
        deployAndAssert(testRepository);
    }

    private void deployAndAssert(Repository testRepository) {
        runEmbeddedMavenBuild(testRepository, "fabric8:deploy", "openshift");

        OpenShiftClient client = new DefaultOpenShiftClient(new ConfigBuilder().build());
        assertThat(client).deployment("spring-boot-rest-http");
        assertThat(client).service("spring-boot-rest-http");
        RouteAssert.assertRoute(client, "spring-boot-rest-http");
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
