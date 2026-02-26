package javi;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JUnit 5 port of the inline {@code stringarrtest()} from
 * {@code EditTester1}.
 *
 * <p>
 * Tests {@link TextEdit#stringtoarray(String)} which splits a string
 * on newline boundaries into an ArrayList.
 * </p>
 */
class StringToArrayJUnitTest {

    @Test
    void trailingEmptyLineAfterText() {
        ArrayList<String> sarr = TextEdit.stringtoarray("1\n\n");
        assertEquals(3, sarr.size());
        assertEquals("1", sarr.get(0));
        assertEquals(0, sarr.get(1).length());
        assertEquals(0, sarr.get(2).length());
    }

    @Test
    void twoLinesWithTrailingNewline() {
        ArrayList<String> sarr = TextEdit.stringtoarray("1\n2\n");
        assertEquals(3, sarr.size());
        assertEquals("1", sarr.get(0));
        assertEquals("2", sarr.get(1));
        assertEquals(0, sarr.get(2).length());
    }

    @Test
    void leadingNewlineTwoLinesTrailingNewline() {
        ArrayList<String> sarr = TextEdit.stringtoarray("\n1\n2\n");
        assertEquals(4, sarr.size());
        assertEquals(0, sarr.get(0).length());
        assertEquals("1", sarr.get(1));
        assertEquals("2", sarr.get(2));
        assertEquals(0, sarr.get(3).length());
    }

    @Test
    void leadingNewlineOneLine() {
        ArrayList<String> sarr = TextEdit.stringtoarray("\n1\n");
        assertEquals(3, sarr.size());
        assertEquals(0, sarr.get(0).length());
        assertEquals("1", sarr.get(1));
        assertEquals(0, sarr.get(2).length());
    }

    @Test
    void singleLineTrailingNewline() {
        ArrayList<String> sarr = TextEdit.stringtoarray("1\n");
        assertEquals(2, sarr.size());
        assertEquals("1", sarr.get(0));
        assertEquals(0, sarr.get(1).length());
    }

    @Test
    void bareNewline() {
        ArrayList<String> sarr = TextEdit.stringtoarray("\n");
        assertEquals(2, sarr.size());
        assertEquals(0, sarr.get(0).length());
        assertEquals(0, sarr.get(1).length());
    }

    @Test
    void leadingNewlineNoTrailing() {
        ArrayList<String> sarr = TextEdit.stringtoarray("\n1");
        assertEquals(2, sarr.size());
        assertEquals(0, sarr.get(0).length());
        assertEquals("1", sarr.get(1));
    }

    @Test
    void twoLinesNoTrailing() {
        ArrayList<String> sarr = TextEdit.stringtoarray("1\n2");
        assertEquals(2, sarr.size());
        assertEquals("1", sarr.get(0));
        assertEquals("2", sarr.get(1));
    }

    @Test
    void leadingNewlineTwoLinesNoTrailing() {
        ArrayList<String> sarr = TextEdit.stringtoarray("\n1\n2");
        assertEquals(3, sarr.size());
        assertEquals(0, sarr.get(0).length());
        assertEquals("1", sarr.get(1));
        assertEquals("2", sarr.get(2));
    }

    @Test
    void singleCharNoNewline() {
        ArrayList<String> sarr = TextEdit.stringtoarray("x");
        assertEquals(1, sarr.size());
        assertEquals("x", sarr.get(0));
    }
}
