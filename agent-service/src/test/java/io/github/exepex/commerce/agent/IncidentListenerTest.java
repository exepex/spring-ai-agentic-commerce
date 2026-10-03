package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.exepex.commerce.agent.exception.HandOffRejectedException;
import io.github.exepex.commerce.agent.exception.HandOffUnreachableException;
import io.github.exepex.commerce.agent.exception.KillSwitchUnreadableException;
import io.github.exepex.commerce.agent.exception.ToolCalledOutsideRunException;
import io.github.exepex.commerce.agent.exception.UnknownAgentException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ExceptionClassifier;
import org.springframework.kafka.support.ExceptionMatcher;

class IncidentListenerTest {

    @Test
    void everyFailedHandOffIsDeliveredAgainAndNoOtherFailureIs() throws ReflectiveOperationException {
        var classifier = retryClassifier();

        assertThat(classifier.match(new KillSwitchUnreadableException("INC0010001", new IllegalStateException()))).isTrue();
        assertThat(classifier.match(new HandOffUnreachableException("INC0010001", "note", new IllegalStateException())))
                .isTrue();
        assertThat(classifier.match(new HandOffRejectedException("INC0010001", "502 Bad Gateway"))).isTrue();
        assertThat(classifier.match(new IllegalStateException("malformed event"))).isFalse();
        // The service's other failures share the hand-offs' base type, but are not retried either.
        assertThat(classifier.match(new UnknownAgentException("someone-else"))).isFalse();
        assertThat(classifier.match(new ToolCalledOutsideRunException())).isFalse();
    }

    /** Spring Kafka keeps the classification to itself; the test reads it to check which failures are retried. */
    private static ExceptionMatcher retryClassifier() throws ReflectiveOperationException {
        var getter = ExceptionClassifier.class.getDeclaredMethod("getExceptionMatcher");
        getter.setAccessible(true);
        var handler = IncidentListener.handOffRetries(mock(DeadLetterPublishingRecoverer.class));
        return (ExceptionMatcher) getter.invoke(handler);
    }
}
