package io.fabirc8.maven.sample.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class HttpApplication extends AbstractVerticle {

    protected static final String template = "Hello, %s!";

    @Override
    public void start(Future<Void> future) {
        // Create a router object.
        Router router = Router.router(vertx);

        router.get("/api/greeting").handler(this::greeting);
        router.get("/*").handler(StaticHandler.create());

        // Create the HTTP server and pass the "accept" method to the request handler.
        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(
                        // Retrieve the port from the configuration, default to 8080.
                        config().getInteger("http.port", 8080), ar -> {
                            if (ar.succeeded()) {
                                System.out.println("Server starter on port " + ar.result().actualPort());
                            }
                            future.handle(ar.mapEmpty());
                        });

    }

    private void greeting(RoutingContext rc) {
        String name = rc.request().getParam("name");
        if (name == null) {
            name = "World";
        }

        JsonObject response = new JsonObject()
                .put("content", String.format(template, name));

        rc.response()
                .putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
                .end(response.encodePrettily());
    }
}