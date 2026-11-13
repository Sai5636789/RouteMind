package com.routemind.optimization;

/** The optimization run itself could not be completed (solver failure, disabled). */
public class OptimizationUnavailableException extends RuntimeException {

    public OptimizationUnavailableException(String message) {
        super(message);
    }
}
