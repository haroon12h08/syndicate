package com.syndicate.common;

import org.slf4j.MDC;

/** Request/operation correlation id, carried in the logging MDC (spec §33, §54). */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
