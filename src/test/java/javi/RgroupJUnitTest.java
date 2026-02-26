package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JUnit 5 tests for the {@link Rgroup} command registration and lookup system.
 *
 * <p>
 * Covers:
 * </p>
 * <ul>
 * <li>{@code bindingLookup} — resolving registered command names</li>
 * <li>{@code oBToInt} / {@code oBToFloat} — argument conversion utilities</li>
 * <li>KeyBinding proto / toString introspection</li>
 * </ul>
 */
class RgroupJUnitTest {

    @BeforeAll
    static void initEditor() throws Exception {
        TestInit.initCommands();
    }

    // --- bindingLookup tests ---

    @Test
    void bindingLookupFindsRegisteredEditCommand() {
        Rgroup.KeyBinding kb = Rgroup.bindingLookup("insert");
        assertNotNull(kb, "insert should be registered by EditGroup");
    }

    @Test
    void bindingLookupFindsRegisteredMoveCommand() {
        Rgroup.KeyBinding kb = Rgroup.bindingLookup("movechar");
        assertNotNull(kb, "movechar should be registered by MoveGroup");
    }

    @Test
    void bindingLookupReturnsNullForUnknownCommand() {
        Rgroup.KeyBinding kb = Rgroup.bindingLookup("nonexistent_command_xyz");
        assertNull(kb, "unknown command should return null");
    }

    @Test
    void bindingLookupFindsDeleteMode() {
        assertNotNull(Rgroup.bindingLookup("deletemode"),
                "deletemode should be registered");
    }

    @Test
    void bindingLookupFindsYankMode() {
        assertNotNull(Rgroup.bindingLookup("yankmode"),
                "yankmode should be registered");
    }

    @Test
    void bindingLookupFindsForwardWord() {
        assertNotNull(Rgroup.bindingLookup("forwardword"),
                "forwardword should be registered by MoveGroup");
    }

    @Test
    void bindingLookupFindsBackwardWord() {
        assertNotNull(Rgroup.bindingLookup("backwardword"),
                "backwardword should be registered by MoveGroup");
    }

    @Test
    void bindingLookupFindsCommandProc() {
        assertNotNull(Rgroup.bindingLookup("commandproc"),
                "commandproc should be registered by MiscCommands");
    }

    @Test
    void keyBindingToStringContainsPipe() {
        Rgroup.KeyBinding kb = Rgroup.bindingLookup("insert");
        assertNotNull(kb);
        String s = kb.toString();
        // format is "Rgroup|arg|index"
        assertNotNull(s);
        assertEquals(2, s.chars().filter(ch -> ch == '|').count(),
                "toString should contain two pipe separators");
    }

    // --- oBToInt tests ---

    @Test
    void oBToIntParsesValidInteger() throws InputException {
        assertEquals(42, Rgroup.oBToInt("42"));
    }

    @Test
    void oBToIntParsesNegativeInteger() throws InputException {
        assertEquals(-7, Rgroup.oBToInt("-7"));
    }

    @Test
    void oBToIntParsesZero() throws InputException {
        assertEquals(0, Rgroup.oBToInt("0"));
    }

    @Test
    void oBToIntTrimsWhitespace() throws InputException {
        assertEquals(99, Rgroup.oBToInt("  99  "));
    }

    @Test
    void oBToIntThrowsOnNull() {
        assertThrows(InputException.class, () -> Rgroup.oBToInt(null));
    }

    @Test
    void oBToIntThrowsOnNonNumeric() {
        assertThrows(InputException.class, () -> Rgroup.oBToInt("abc"));
    }

    @Test
    void oBToIntThrowsOnFloat() {
        assertThrows(InputException.class, () -> Rgroup.oBToInt("3.14"));
    }

    @Test
    void oBToIntThrowsOnEmpty() {
        assertThrows(InputException.class, () -> Rgroup.oBToInt("  "));
    }

    // --- oBToFloat tests ---

    @Test
    void oBToFloatParsesValidFloat() throws InputException {
        assertEquals(3.14f, Rgroup.oBToFloat("3.14"), 0.001f);
    }

    @Test
    void oBToFloatParsesNegativeFloat() throws InputException {
        assertEquals(-0.5f, Rgroup.oBToFloat("-0.5"), 0.001f);
    }

    @Test
    void oBToFloatParsesInteger() throws InputException {
        assertEquals(42.0f, Rgroup.oBToFloat("42"), 0.001f);
    }

    @Test
    void oBToFloatTrimsWhitespace() throws InputException {
        assertEquals(1.0f, Rgroup.oBToFloat("  1.0  "), 0.001f);
    }

    @Test
    void oBToFloatThrowsOnNull() {
        assertThrows(InputException.class, () -> Rgroup.oBToFloat(null));
    }

    @Test
    void oBToFloatThrowsOnNonNumeric() {
        assertThrows(InputException.class, () -> Rgroup.oBToFloat("xyz"));
    }
}
