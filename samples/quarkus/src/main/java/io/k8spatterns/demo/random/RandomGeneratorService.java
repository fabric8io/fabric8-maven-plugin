package io.k8spatterns.demo.random;

import java.util.Random;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RandomGeneratorService {

    private static UUID id = UUID.randomUUID();
    private static Random random = new Random();

    public int getRandom() {
        return random.nextInt();
    }

    public UUID getUUID() {
        return id;
    }
}
