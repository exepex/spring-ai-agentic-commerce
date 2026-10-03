package io.github.exepex.commerce.platform.security;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How an agent presents its token, and how a service reads it back: an {@code Authorization: Bearer} header. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BearerTokens {

    static final String PREFIX = "Bearer ";

    /** The {@code Authorization} header value that presents {@code token}. */
    public static String authorization(String token) {
        return PREFIX + token;
    }

    /** The token an {@code Authorization} header presents, if it presents one. */
    public static Optional<String> tokenIn(String authorization) {
        return authorization != null && authorization.startsWith(PREFIX)
                ? Optional.of(authorization.substring(PREFIX.length()))
                : Optional.empty();
    }
}
