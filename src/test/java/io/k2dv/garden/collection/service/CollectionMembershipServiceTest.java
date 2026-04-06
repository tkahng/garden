package io.k2dv.garden.collection.service;

import io.k2dv.garden.collection.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionMembershipServiceTest {

    private CollectionRule rule(CollectionRuleField field, CollectionRuleOperator op, String value) {
        CollectionRule r = new CollectionRule();
        r.setField(field);
        r.setOperator(op);
        r.setValue(value);
        return r;
    }

    @Test
    void equalsRule_matchesExactTagCaseInsensitive() {
        var tags = Set.of("SALE", "new");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void equalsRule_noMatch_returnsFalse() {
        var tags = Set.of("new");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isFalse();
    }

    @Test
    void notEqualsRule_tagAbsent_returnsTrue() {
        var tags = Set.of("new");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.NOT_EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void notEqualsRule_tagPresent_returnsFalse() {
        var tags = Set.of("sale");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.NOT_EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isFalse();
    }

    @Test
    void containsRule_substringMatch_returnsTrue() {
        var tags = Set.of("summer-sale");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.CONTAINS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void containsRule_caseInsensitive() {
        var tags = Set.of("SUMMER-SALE");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.CONTAINS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void containsRule_noSubstringMatch_returnsFalse() {
        var tags = Set.of("new");
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.CONTAINS, "sale"));
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isFalse();
    }

    @Test
    void andLogic_allRulesMustMatch() {
        var tags = Set.of("sale", "new");
        var rules = List.of(
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"),
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "new")
        );
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isTrue();
    }

    @Test
    void andLogic_oneRuleFails_returnsFalse() {
        var tags = Set.of("sale");
        var rules = List.of(
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"),
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "new")
        );
        assertThat(CollectionMembershipService.evaluate(tags, rules, false)).isFalse();
    }

    @Test
    void orLogic_oneRuleMatches_returnsTrue() {
        var tags = Set.of("sale");
        var rules = List.of(
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"),
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "new")
        );
        assertThat(CollectionMembershipService.evaluate(tags, rules, true)).isTrue();
    }

    @Test
    void orLogic_noRuleMatches_returnsFalse() {
        var tags = Set.of("other");
        var rules = List.of(
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"),
            rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "new")
        );
        assertThat(CollectionMembershipService.evaluate(tags, rules, true)).isFalse();
    }

    @Test
    void emptyRules_returnsFalse() {
        var tags = Set.of("sale");
        assertThat(CollectionMembershipService.evaluate(tags, List.of(), false)).isFalse();
    }

    @Test
    void emptyTags_noRuleMatches() {
        var rules = List.of(rule(CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(CollectionMembershipService.evaluate(Set.of(), rules, false)).isFalse();
    }
}
