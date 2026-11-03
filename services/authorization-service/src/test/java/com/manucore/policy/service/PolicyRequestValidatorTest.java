package com.manucore.policy.service;

import com.manucore.policy.dto.PolicyDtos.ConditionDto;
import com.manucore.policy.dto.PolicyDtos.ConditionGroupDto;
import com.manucore.policy.dto.PolicyDtos.PolicyRequest;
import com.manucore.policy.dto.PolicyDtos.SubjectDto;
import com.manucore.policy.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyRequestValidatorTest {

    private PolicyRequest request(List<ConditionGroupDto> groups) {
        return new PolicyRequest(
                "name", "description", "READ", "INVENTORY.ITEMS",
                Effect.ALLOW, null,
                List.of(new SubjectDto(SubjectType.ROLE, "ADMIN")),
                groups);
    }

    private ConditionGroupDto groupOf(ConditionAttribute attribute,
                                      ConditionOperator operator, String value) {
        return new ConditionGroupDto(
                LogicalOperator.AND,
                List.of(new ConditionDto(attribute, operator, value)),
                null);
    }

    private ConditionGroupDto chain(int childLevels) {
        ConditionGroupDto current = groupOf(
                ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK");
        for (int i = 0; i < childLevels; i++) {
            current = new ConditionGroupDto(
                    LogicalOperator.AND,
                    List.of(new ConditionDto(
                            ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK")),
                    List.of(current));
        }
        return current;
    }

    @Test
    void acceptsPolicyWithoutConditions() {
        assertThatCode(() -> PolicyRequestValidator.validate(request(null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> PolicyRequestValidator.validate(request(List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsWellFormedConditions() {
        assertThatCode(() -> PolicyRequestValidator.validate(request(List.of(
                groupOf(ConditionAttribute.TIME, ConditionOperator.BETWEEN, "09:00,17:00"),
                groupOf(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR, "10.0.0.0/8"),
                groupOf(ConditionAttribute.COUNTRY, ConditionOperator.IN, "LK,IN")))))
                .doesNotThrowAnyException();
    }

    @Test
    void existsDoesNotNeedAValue() {
        assertThatCode(() -> PolicyRequestValidator.validate(request(List.of(
                groupOf(ConditionAttribute.DEVICE_ID, ConditionOperator.EXISTS, null)))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyGroup() {
        assertThatThrownBy(() -> PolicyRequestValidator.validate(request(List.of(
                new ConditionGroupDto(LogicalOperator.AND, List.of(), List.of())))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMalformedBetween() {
        assertThatThrownBy(() -> PolicyRequestValidator.validate(request(List.of(
                groupOf(ConditionAttribute.TIME, ConditionOperator.BETWEEN, "09:00")))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMalformedCidr() {
        assertThatThrownBy(() -> PolicyRequestValidator.validate(request(List.of(
                groupOf(ConditionAttribute.IP_ADDRESS, ConditionOperator.IN_CIDR, "10.0.0.0")))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsBlankValueForEquals() {
        assertThatThrownBy(() -> PolicyRequestValidator.validate(request(List.of(
                groupOf(ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "  ")))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsEmptyInList() {
        assertThatThrownBy(() -> PolicyRequestValidator.validate(request(List.of(
                groupOf(ConditionAttribute.COUNTRY, ConditionOperator.IN, " , ")))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptsNestingUpToTheEngineLimitAndRejectsBeyondIt() {
        assertThatCode(() -> PolicyRequestValidator.validate(
                request(List.of(chain(PolicyEngine.MAX_GROUP_DEPTH)))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> PolicyRequestValidator.validate(
                request(List.of(chain(PolicyEngine.MAX_GROUP_DEPTH + 1)))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsTooManyConditions() {
        List<ConditionDto> many = java.util.stream.IntStream.range(0, 201)
                .mapToObj(i -> new ConditionDto(
                        ConditionAttribute.COUNTRY, ConditionOperator.EQUALS, "LK"))
                .toList();

        assertThatThrownBy(() -> PolicyRequestValidator.validate(request(List.of(
                new ConditionGroupDto(LogicalOperator.AND, many, null)))))
                .isInstanceOf(ResponseStatusException.class);
    }
}