package com.zoufx.ai.agent.util;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import reactor.core.Exceptions;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

public final class RetryPolicy {

    private RetryPolicy() {
    }

    public static boolean isRetryable(Throwable t) {
        Throwable e = Exceptions.unwrap(t);
        if (e instanceof NonRetriableException) return false;
        if (e instanceof RetriableException) return true;
        if (e instanceof HttpException he) {
            int s = he.statusCode();
            return s == 408 || s == 429 || s >= 500;
        }
        return e instanceof SocketTimeoutException
                || e instanceof ConnectException
                || e instanceof IOException;
    }
}
