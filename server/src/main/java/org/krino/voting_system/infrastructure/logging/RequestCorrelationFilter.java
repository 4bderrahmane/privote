package org.krino.voting_system.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class RequestCorrelationFilter extends OncePerRequestFilter
{
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    private static final Logger accessLog = LoggerFactory.getLogger("HTTP_ACCESS");
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException
    {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        String previousRequestId = MDC.get(MDC_REQUEST_ID_KEY);
        long startedAt = System.nanoTime();
        Exception failure = null;

        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        try
        {
            filterChain.doFilter(request, response);
        }
        catch (IOException | ServletException | RuntimeException ex)
        {
            failure = ex;
            throw ex;
        }
        finally
        {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = resolveStatus(response, failure);

            if (status >= 500)
            {
                accessLog.warn("HTTP request completed method={} path={} status={} durationMs={} requestId={}",
                        request.getMethod(), request.getRequestURI(), status, durationMs, requestId);
            }
            else
            {
                accessLog.info("HTTP request completed method={} path={} status={} durationMs={} requestId={}",
                        request.getMethod(), request.getRequestURI(), status, durationMs, requestId);
            }

            restorePreviousRequestId(previousRequestId);
        }
    }

    private static int resolveStatus(HttpServletResponse response, Exception failure)
    {
        if (failure == null)
        {
            return response.getStatus();
        }
        return response.getStatus() >= 400 ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    private static void restorePreviousRequestId(String previousRequestId)
    {
        if (previousRequestId == null)
        {
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
        else
        {
            MDC.put(MDC_REQUEST_ID_KEY, previousRequestId);
        }
    }

    private static String resolveRequestId(String candidate)
    {
        if (candidate == null)
        {
            return UUID.randomUUID().toString();
        }

        String normalized = candidate.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_REQUEST_ID_LENGTH || !SAFE_REQUEST_ID.matcher(normalized).matches())
        {
            return UUID.randomUUID().toString();
        }

        return normalized;
    }
}
