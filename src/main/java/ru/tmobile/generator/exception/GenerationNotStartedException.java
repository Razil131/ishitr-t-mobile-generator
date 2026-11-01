package ru.tmobile.generator.exception;

import ru.tmobile.generator.model.ResourceType;

public class GenerationNotStartedException extends RuntimeException {
    public GenerationNotStartedException(Long userId, ResourceType type) {
        super(String.format("Generation type %s is not started for user %d", type, userId));
    }
}
