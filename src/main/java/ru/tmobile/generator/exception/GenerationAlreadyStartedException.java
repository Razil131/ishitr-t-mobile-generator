package ru.tmobile.generator.exception;

import ru.tmobile.generator.model.ResourceType;

public class GenerationAlreadyStartedException extends RuntimeException {
    public GenerationAlreadyStartedException(Long userId, ResourceType type) {
        super(String.format("Generation type %s already started for user %d", type, userId));
    }
}