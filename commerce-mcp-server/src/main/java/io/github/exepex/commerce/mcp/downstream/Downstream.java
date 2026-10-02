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
            String message = retryable
                    ? "The " + service + " is unavailable (" + detailOf(refused) + "). It is safe to retry later."
                    : detailOf(refused);
            throw new DownstreamException(refused.getStatusCode(), message, retryable, refused);
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

    private static String detailOf(HttpStatusCodeException refused) {
        try {
            ProblemDetail problem = refused.getResponseBodyAs(ProblemDetail.class);
            if (problem != null && problem.getDetail() != null) {
                return problem.getDetail();
            }
        } catch (RuntimeException unreadableBody) {
            // fall through to the status text
        }
        return refused.getStatusCode().toString();
    }
}
