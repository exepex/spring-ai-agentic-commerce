package io.github.exepex.commerce.mcp.downstream;

import io.github.exepex.commerce.mcp.exception.DownstreamException;
import io.github.exepex.commerce.mcp.exception.DownstreamRefusedException;
import io.github.exepex.commerce.mcp.exception.DownstreamUnavailableException;
import io.github.exepex.commerce.mcp.exception.DownstreamUnreachableException;
import java.util.Map;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

/**
 * Runs a call to a commerce service and turns any HTTP failure into a {@link DownstreamException}: a server error or
 * no answer at all is worth retrying later, any other refusal is not. The service's own problem detail and its extra
 * properties are kept.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Downstream {

    public static <T> T call(String service, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException refused) {
            var status = refused.getStatusCode();
            var problem = problemOf(refused);
            var detail = problem != null && problem.getDetail() != null ? problem.getDetail() : status.toString();
            var properties = problem != null && problem.getProperties() != null
                    ? problem.getProperties()
                    : Map.<String, Object>of();
            if (status.is5xxServerError()) {
                throw new DownstreamUnavailableException(service, status, detail, properties, refused);
            }
            throw new DownstreamRefusedException(status, detail, properties, refused);
        } catch (RestClientException unreachable) {
            throw new DownstreamUnreachableException(service, unreachable);
        }
    }

    private static ProblemDetail problemOf(HttpStatusCodeException refused) {
        try {
            return refused.getResponseBodyAs(ProblemDetail.class);
        } catch (RuntimeException unreadableBody) {
            return null;
        }
    }
}
