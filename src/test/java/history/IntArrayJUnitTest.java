package history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntArrayJUnitTest {

    @Test
    void growthBeyondInitialCapacity() {
        IntArray arr = new IntArray(2);

        for (int i = 0; i < 10; i++) {
           arr.add(i * 10);
        }

        assertEquals(10, arr.size());
        for (int i = 0; i < 10; i++) {
           assertEquals(i * 10, arr.get(i));
        }
    }

    @Test
    void setModifiesValueAtIndex() {
        IntArray arr = new IntArray(4);
        arr.add(10);
        arr.add(20);
        arr.add(30);

        arr.set(1, 99);

        assertEquals(99, arr.get(1));
        assertEquals(10, arr.get(0));
        assertEquals(30, arr.get(2));
    }

    @Test
    void removeRangeFromFrontReducesSize() {
        IntArray arr = new IntArray(8);
        arr.add(10);
        arr.add(20);
        arr.add(30);
        arr.add(40);
        arr.add(50);

        arr.removeRange(0, 2);

        assertEquals(3, arr.size());
        assertEquals(30, arr.get(0));
        assertEquals(40, arr.get(1));
        assertEquals(50, arr.get(2));
    }

    @Test
    void addAndGetMaintainsOrder() {
        IntArray arr = new IntArray(2);
        arr.add(10);
        arr.add(20);
        arr.add(30);

        assertEquals(3, arr.size());
        assertEquals(10, arr.get(0));
        assertEquals(20, arr.get(1));
        assertEquals(30, arr.get(2));
    }

    @Test
    void removeRangeRemovesMiddleSlice() {
        IntArray arr = new IntArray(8);
        arr.add(10);
        arr.add(20);
        arr.add(30);
        arr.add(40);
        arr.add(50);

        arr.removeRange(1, 3);

        assertEquals(3, arr.size());
        assertEquals(10, arr.get(0));
        assertEquals(40, arr.get(1));
        assertEquals(50, arr.get(2));
    }

    @Test
    void getOutOfBoundsThrows() {
        IntArray arr = new IntArray(1);
        arr.add(42);

        assertThrows(IndexOutOfBoundsException.class, () -> arr.get(1));
    }
}
