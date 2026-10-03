package io.github.exepex.commerce.order;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpClientErrorException;

/** Reads the problem details another service answered with. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RemoteProblems {

    /** The other service's own explanation of the failure, or {@code fallback} when it gave none. */
    static String detailOf(HttpClientErrorException failure, String fallback) {
        return failure.getResponseBodyAs(ProblemDetail.class) instanceof ProblemDetail problem
                && problem.getDetail() != null ? problem.getDetail() : fallback;
    }
}
