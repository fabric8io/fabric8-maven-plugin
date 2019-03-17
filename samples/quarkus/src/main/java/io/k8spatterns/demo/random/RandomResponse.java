package io.k8spatterns.demo.random;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RandomResponse {

    private UUID id;
    private int random;

    RandomResponse(UUID id, int random) {
        this.id = id;
        this.random = random;
    }

    public String getId() {
        return id.toString();
    }

    public int getRandom() {
        return random;
    }
}
