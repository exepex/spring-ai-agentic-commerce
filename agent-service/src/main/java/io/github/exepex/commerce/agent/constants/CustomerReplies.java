package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the shopping assistant answers when it cannot ask the model. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CustomerReplies {

    public static final String TRY_AGAIN = "Sorry, I could not answer that just now. Please try again in a moment.";
    public static final String ASSISTANT_SWITCHED_OFF =
            "The shopping assistant is switched off right now. Please try again later.";
}
