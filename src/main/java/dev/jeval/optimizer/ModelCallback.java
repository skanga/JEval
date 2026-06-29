package dev.jeval.optimizer;

import dev.jeval.prompt.Prompt;

@FunctionalInterface
public interface ModelCallback {
    String generate(Prompt prompt, Object golden);
}
