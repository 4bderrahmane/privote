package org.privote.backend.infrastructure.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RequestCorrelationFilterTest
{
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void generatesRequestIdWhenHeaderIsMissing() throws ServletException, IOException
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/elections");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertEquals(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER), MDC.get("requestId")));

        String requestId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
        assertNull(MDC.get("requestId"));
    }

    @Test
    void preservesSafeIncomingRequestId() throws ServletException, IOException
    {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/parties/create");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "edge-proxy-req-123");

        filter.doFilter(request, response, (req, res) ->
                assertEquals("edge-proxy-req-123", MDC.get("requestId")));

        assertEquals("edge-proxy-req-123", response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void replacesUnsafeIncomingRequestId() throws ServletException, IOException
    {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/candidates/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "bad request id");

        filter.doFilter(request, response, (req, res) ->
                assertNotEquals("bad request id", MDC.get("requestId")));

        assertNotEquals("bad request id", response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get("requestId"));
    }
}
