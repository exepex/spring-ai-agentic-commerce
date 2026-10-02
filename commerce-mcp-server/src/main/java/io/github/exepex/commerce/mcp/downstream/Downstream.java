package io.github.exepex.commerce.mcp.downstream;

import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

/** Runs a call to a commerce service and turns any HTTP failure into a {@link DownstreamException}. */
public final class Downstream {

    private Downstream() {}

    public static <T> T call(String service, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException refused) {
            boolean retryable = refused.getStatusCode().is5xxServerError();
            ProblemDetail problem = problemOf(refused);
            String detail = problem != null && problem.getDetail() != null ? problem.getDetail() : refused.getStatusCode().toString();
            String message = retryable
                    ? "The " + service + " is unavailable (" + detail + "). It is safe to retry later."
                    : detail;
            DownstreamException failure = new DownstreamException(refused.getStatusCode(), message, retryable, refused);
            if (problem != null && problem.getProperties() != null) {
                problem.getProperties().forEach(failure.getBody()::setProperty);
            }
            throw failure;
        } catch (RestClientException unreachable) {
            throw new DownstreamException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The " + service + " could not be reached. It is safe to retry later.", true, unreachable);
        }
    }

    public static void run(String service, Runnable request) {
        call(service, () -> {
            request.run();
            return null;
        });
    }

    private static ProblemDetail problemOf(HttpStatusCodeException refused) {
        try {
            return refused.getResponseBodyAs(ProblemDetail.class);
        } catch (RuntimeException unreadableBody) {
            return null;
        }
    }
}
