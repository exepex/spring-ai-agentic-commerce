package io.github.exepex.commerce.platform.remote;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.RestClientResponseException;

/** What another service said when it refused a request: its RFC 9457 problem detail, when it sent a readable one. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RemoteProblems {

    public static Optional<ProblemDetail> problemOf(RestClientResponseException refused) {
        try {
            return Optional.ofNullable(refused.getResponseBodyAs(ProblemDetail.class));
        } catch (RuntimeException unreadableBody) {
            return Optional.empty();
        }
    }

    /** The other service's own explanation of the failure, or {@code fallback} when it gave none. */
    public static String detailOf(RestClientResponseException refused, String fallback) {
        return problemOf(refused).map(ProblemDetail::getDetail).orElse(fallback);
    }
}
