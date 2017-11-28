package jar;

import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * Created by hshinde on 11/24/17.
 */

public class RouteAssert {

    private RouteAssert(){

    }

    public static void assertRoute(OpenShiftClient client, String appName) {
        for (Route route : client.routes().list().getItems()) {
            if(route.getMetadata().getName().equalsIgnoreCase(appName))
            {
                return;
            }
        }

        throw new AssertionError("[No route exists for name: "+appName+"] \n" +
                "Expecting actual not to be null");
    }

}
