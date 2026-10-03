package io.github.exepex.commerce.mcp.cases;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exepex.commerce.governance.api.dto.CaseStatus;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The governance contract names each case status as the case does, both ways. */
class CaseMapperTest {

    @Test
    void everyStatusOfTheContractIsACaseStatusAndBack() {
        assertThat(Arrays.stream(CaseStatus.values()).map(CaseMapper::toStatus).map(Enum::name))
                .containsExactlyElementsOf(Arrays.stream(SupportCase.Status.values()).map(Enum::name).toList());
    }
}
