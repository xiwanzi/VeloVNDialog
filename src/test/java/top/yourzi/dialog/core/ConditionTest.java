package top.yourzi.dialog.core;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConditionTest {
    private final TestContext context = new TestContext();
    private boolean matches(String json) { return Condition.compile(JsonParser.parseString(json)).test(context); }

    @Test void comparisonsAreTypedAndPermissionCompositionShortCircuits() {
        context.state.set("faction", new JsonPrimitive("a"));
        context.state.set("rep", new JsonPrimitive(12));
        context.permissions.add("guard.enter");
        assertTrue(matches("{\"permission\":\"guard.enter\",\"var\":{\"faction\":\"a\",\"rep\":{\"gte\":10,\"lt\":20}}}"));
        assertFalse(matches("{\"var\":{\"rep\":\"12\"}}"));
        assertTrue(matches("{\"var\":{\"rep\":{\"eq\":12.0,\"ne\":13},\"unset\":{\"exists\":false}}}"));
        assertTrue(matches("{\"any\":[{\"permission\":\"missing\"},{\"var\":{\"faction\":\"a\"}}]}"));
        assertFalse(matches("{\"all\":[{\"permission\":\"missing\"},{\"var\":{\"faction\":\"a\"}}]}"));
        assertTrue(matches("{\"not\":{\"permission\":\"missing\"}}"));
    }

    @ParameterizedTest @ValueSource(strings={"{\"any\":[]}","{\"not\":null}","{\"all\":[null]}","{\"permission\":4}","{\"var\":{\"rep\":{\"gte\":\"10\"}}}","{\"var\":{\"rep\":{\"gtee\":10}}}"})
    void invalidConditionsFailAtCompileTime(String json) { assertThrows(IllegalArgumentException.class, () -> Condition.compile(JsonParser.parseString(json))); }

    @Test void badNumericMutationDoesNotPartiallyChangeOtherVariables() {
        context.state.set("rep", new JsonPrimitive("not a number"));
        assertThrows(IllegalArgumentException.class, () -> context.state.prepare(List.of(
            new PlayerVariables.Mutation("faction", new JsonPrimitive("a"), false),
            new PlayerVariables.Mutation("rep", new JsonPrimitive(1), true))));
        assertTrue(context.state.get("faction").isJsonNull());
    }
}
