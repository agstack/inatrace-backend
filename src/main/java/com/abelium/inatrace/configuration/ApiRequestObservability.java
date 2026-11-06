package com.abelium.inatrace.configuration;

import com.abelium.inatrace.security.service.CustomUserDetails;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Request-scoped diagnostics. No request body, raw URL, token or IP is recorded. */
@Configuration
public class ApiRequestObservability implements WebMvcConfigurer {
    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "request_id";
    private static final String STATE_ATTRIBUTE = ApiRequestObservability.class.getName() + ".state";
    private static final Logger logger = LoggerFactory.getLogger(ApiRequestObservability.class);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");

    @Bean
    public FilterRegistrationBean<RequestFilter> apiRequestObservabilityFilter() {
        FilterRegistrationBean<RequestFilter> registration = new FilterRegistrationBean<>(new RequestFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    public ServletListenerRegistrationBean<CompletionListener> apiRequestCompletionListener() {
        return new ServletListenerRegistrationBean<>(new CompletionListener());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RouteInterceptor());
    }

    static final class RouteInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            State state = state(request);
            if (state != null && request.getDispatcherType() != DispatcherType.ERROR) {
                state.captureRoute(request);
            }
            return true;
        }
    }

    /** Called after authentication, before the security context is cleared. */
    public static void captureActor(HttpServletRequest request, CustomUserDetails principal) {
        State state = state(request);
        if (state != null && principal != null) {
            state.userId = principal.getUserId();
            state.role = principal.getUserRole() == null ? null : principal.getUserRole().name();
        }
    }

    public static String requestId() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "none" : id;
    }

    static String chooseId(List<String> values) {
        if (values.size() == 1 && UUID_PATTERN.matcher(values.get(0)).matches()) {
            return UUID.fromString(values.get(0)).toString();
        }
        return UUID.randomUUID().toString();
    }

    private static State state(ServletRequest request) {
        Object value = request.getAttribute(STATE_ATTRIBUTE);
        return value instanceof State ? (State) value : null;
    }

    private static void restoreMdc(Map<String, String> previous) {
        if (previous == null) MDC.clear();
        else MDC.setContextMap(previous);
    }

    static final class State {
        final String id;
        final String method;
        final long start = System.nanoTime();
        final AtomicBoolean completed = new AtomicBoolean();
        volatile HttpServletResponse response;
        volatile String route = "UNMATCHED";
        volatile Long userId;
        volatile String role;

        State(String id, String method, HttpServletResponse response) {
            this.id = id;
            this.method = METHODS.contains(method) ? method : "OTHER";
            this.response = response;
        }

        synchronized void captureRoute(ServletRequest request) {
            if (!"UNMATCHED".equals(route)) return;
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern == null) return;
            String mappedRoute = pattern.toString();
            if (!"/error".equals(mappedRoute) && !"/**".equals(mappedRoute)) route = mappedRoute;
        }

        void complete() {
            if (!completed.compareAndSet(false, true)) return;
            long durationMs = Math.max(0, (System.nanoTime() - start) / 1_000_000);
            if (userId == null) {
                logger.info("API request completed request_id={} method={} route={} status={} duration_ms={}",
                        id, method, route, response.getStatus(), durationMs);
            } else {
                logger.info("API request completed request_id={} method={} route={} status={} duration_ms={} user_id={} role={}",
                        id, method, route, response.getStatus(), durationMs, userId, role);
            }
        }
    }

    public static final class RequestFilter extends OncePerRequestFilter {
        @Override
        protected boolean shouldNotFilterAsyncDispatch() { return false; }

        @Override
        protected boolean shouldNotFilterErrorDispatch() { return false; }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String apiPath = request.getContextPath() + "/api";
            String uri = request.getRequestURI();
            return state(request) == null && !uri.equals(apiPath) && !uri.startsWith(apiPath + "/");
        }

        @Override
        protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
                                                   FilterChain chain) throws ServletException, IOException {
            doFilterInternal(request, response, chain);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            State state = state(request);
            if (state == null) {
                List<String> ids = Collections.list(request.getHeaders(HEADER));
                state = new State(chooseId(ids), request.getMethod().toUpperCase(Locale.ROOT), response);
                request.setAttribute(STATE_ATTRIBUTE, state);
            }
            CorrelatedResponse wrappedResponse = new CorrelatedResponse(response, state.id);
            state.response = wrappedResponse;
            if (!response.isCommitted()) wrappedResponse.setHeader(HEADER, state.id);
            HttpServletRequest wrappedRequest = new AsyncAwareRequest(request, wrappedResponse, state);
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.put(MDC_KEY, state.id);
            try {
                chain.doFilter(wrappedRequest, wrappedResponse);
            } finally {
                if (request.getDispatcherType() != DispatcherType.ERROR) state.captureRoute(request);
                restoreMdc(previous);
            }
        }
    }

    static final class CompletionListener implements ServletRequestListener {
        @Override
        public void requestDestroyed(ServletRequestEvent event) {
            State state = state(event.getServletRequest());
            if (state != null) state.complete();
        }
    }

    private static final class CorrelatedResponse extends HttpServletResponseWrapper {
        private final String id;

        CorrelatedResponse(HttpServletResponse response, String id) {
            super(response);
            this.id = id;
        }

        @Override
        public void reset() {
            super.reset();
            setHeader(HEADER, id);
        }
    }

    private static final class AsyncAwareRequest extends HttpServletRequestWrapper {
        private final HttpServletResponse response;
        private final State state;

        AsyncAwareRequest(HttpServletRequest request, HttpServletResponse response, State state) {
            super(request);
            this.response = response;
            this.state = state;
        }

        @Override
        public jakarta.servlet.AsyncContext startAsync() throws IllegalStateException {
            return startAsync(this, response);
        }

        @Override
        public jakarta.servlet.AsyncContext startAsync(ServletRequest request, ServletResponse response)
                throws IllegalStateException {
            jakarta.servlet.AsyncContext context = super.startAsync(request, response);
            context.addListener(new AsyncCompletionListener(state));
            return context;
        }
    }

    private static final class AsyncCompletionListener implements AsyncListener {
        private final State state;

        AsyncCompletionListener(State state) { this.state = state; }

        @Override
        public void onComplete(AsyncEvent event) {
            state.complete();
        }

        // Timeout and error handlers may still dispatch or change the response.
        // Wait for onComplete (or requestDestroyed) to record the final status.
        @Override
        public void onTimeout(AsyncEvent event) { }

        @Override
        public void onError(AsyncEvent event) { }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}
