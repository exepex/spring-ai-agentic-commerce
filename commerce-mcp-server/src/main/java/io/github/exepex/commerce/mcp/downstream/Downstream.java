package io.github.exepex.commerce.mcp.downstream;

import io.github.exepex.commerce.mcp.exception.DownstreamException;
import io.github.exepex.commerce.platform.remote.RemoteProblems;
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
            var problem = RemoteProblems.problemOf(refused);
            var detail = problem.map(ProblemDetail::getDetail).orElse(status.toString());
            var properties = problem.map(ProblemDetail::getProperties).orElse(Map.of());
            if (status.is5xxServerError()) {
                throw DownstreamException.unavailable(service, status, detail, properties, refused);
            }
            throw DownstreamException.refused(status, detail, properties, refused);
        } catch (RestClientException unreachable) {
            throw DownstreamException.unreachable(service, unreachable);
        }
    }
}
