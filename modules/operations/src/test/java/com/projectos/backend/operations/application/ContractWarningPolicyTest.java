package com.projectos.backend.operations.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractWarningPolicyTest {
    @Test
    void classifiesEveryBoundaryIntoExactlyOneUrgency() {
        assertThat(ContractWarningPolicy.urgency(-1)).isEqualTo("overdue");
        assertThat(ContractWarningPolicy.urgency(0)).isEqualTo("critical");
        assertThat(ContractWarningPolicy.urgency(15)).isEqualTo("critical");
        assertThat(ContractWarningPolicy.urgency(16)).isEqualTo("warning");
        assertThat(ContractWarningPolicy.urgency(30)).isEqualTo("warning");
        assertThat(ContractWarningPolicy.urgency(31)).isEqualTo("upcoming");
        assertThat(ContractWarningPolicy.urgency(60)).isEqualTo("upcoming");
    }
}
