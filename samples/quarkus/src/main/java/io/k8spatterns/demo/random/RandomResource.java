package io.k8spatterns.demo.random;


import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
public class RandomResource {

    @Inject
    RandomGeneratorService service;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RandomResponse random() {
        return new RandomResponse(service.getUUID(), service.getRandom());
    }
}