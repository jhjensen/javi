package history;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for {@link ByteInput}, a memory-backed DataInput
 * that reads serialized data from byte arrays.
 */
class ByteInputJUnitTest {

   /** Helper: write primitives to a byte array via DataOutputStream. */
   private byte[] writeData(DataWriter writer) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      writer.write(dos);
      dos.flush();
      return baos.toByteArray();
   }

   @FunctionalInterface
   private interface DataWriter {
      void write(DataOutputStream dos) throws IOException;
   }

   // --- readInt ---

   @Test
   @DisplayName("readInt reads big-endian 4-byte integer")
   void readIntBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeInt(42));
      ByteInput bi = new ByteInput(data);
      assertEquals(42, bi.readInt());
   }

   @Test
   @DisplayName("readInt handles negative values")
   void readIntNegative() throws IOException {
      byte[] data = writeData(dos -> dos.writeInt(-12345));
      ByteInput bi = new ByteInput(data);
      assertEquals(-12345, bi.readInt());
   }

   @Test
   @DisplayName("readInt handles Integer.MAX_VALUE")
   void readIntMaxValue() throws IOException {
      byte[] data = writeData(dos -> dos.writeInt(Integer.MAX_VALUE));
      ByteInput bi = new ByteInput(data);
      assertEquals(Integer.MAX_VALUE, bi.readInt());
   }

   @Test
   @DisplayName("readInt handles Integer.MIN_VALUE")
   void readIntMinValue() throws IOException {
      byte[] data = writeData(dos -> dos.writeInt(Integer.MIN_VALUE));
      ByteInput bi = new ByteInput(data);
      assertEquals(Integer.MIN_VALUE, bi.readInt());
   }

   // --- readLong ---

   @Test
   @DisplayName("readLong reads big-endian 8-byte long")
   void readLongBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeLong(123456789012345L));
      ByteInput bi = new ByteInput(data);
      assertEquals(123456789012345L, bi.readLong());
   }

   @Test
   @DisplayName("readLong handles negative values")
   void readLongNegative() throws IOException {
      byte[] data = writeData(dos -> dos.writeLong(-999999999999L));
      ByteInput bi = new ByteInput(data);
      assertEquals(-999999999999L, bi.readLong());
   }

   // --- readShort / readUnsignedShort ---

   @Test
   @DisplayName("readShort reads big-endian 2-byte short")
   void readShortBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeShort(1234));
      ByteInput bi = new ByteInput(data);
      assertEquals((short) 1234, bi.readShort());
   }

   @Test
   @DisplayName("readUnsignedShort reads 2-byte unsigned value")
   void readUnsignedShortBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeShort(60000));
      ByteInput bi = new ByteInput(data);
      assertEquals(60000, bi.readUnsignedShort());
   }

   // --- readByte / readUnsignedByte ---

   @Test
   @DisplayName("readByte reads single signed byte")
   void readByteBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeByte(100));
      ByteInput bi = new ByteInput(data);
      assertEquals((byte) 100, bi.readByte());
   }

   @Test
   @DisplayName("readUnsignedByte reads single unsigned byte")
   void readUnsignedByteValue() throws IOException {
      byte[] data = writeData(dos -> dos.writeByte(200));
      ByteInput bi = new ByteInput(data);
      assertEquals(200, bi.readUnsignedByte());
   }

   // --- readBoolean ---

   @Test
   @DisplayName("readBoolean reads 0 as true (inverted convention)")
   void readBooleanTrue() throws IOException {
      // ByteInput uses: return 0 == buf[offset++]  (inverted from DataInput)
      byte[] data = writeData(dos -> dos.writeBoolean(false));
      ByteInput bi = new ByteInput(data);
      // DataOutputStream.writeBoolean(false) writes 0
      // ByteInput.readBoolean() returns (0 == 0) => true
      assertTrue(bi.readBoolean());
   }

   @Test
   @DisplayName("readBoolean reads non-zero as false (inverted convention)")
   void readBooleanFalse() throws IOException {
      byte[] data = writeData(dos -> dos.writeBoolean(true));
      ByteInput bi = new ByteInput(data);
      // DataOutputStream.writeBoolean(true) writes 1
      // ByteInput.readBoolean() returns (0 == 1) => false
      assertFalse(bi.readBoolean());
   }

   // --- readChar ---

   @Test
   @DisplayName("readChar reads 2-byte character")
   void readCharBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeChar('Z'));
      ByteInput bi = new ByteInput(data);
      assertEquals('Z', bi.readChar());
   }

   // --- readFloat / readDouble ---

   @Test
   @DisplayName("readFloat reads IEEE 754 float")
   void readFloatBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeFloat(3.14f));
      ByteInput bi = new ByteInput(data);
      assertEquals(3.14f, bi.readFloat(), 0.001f);
   }

   @Test
   @DisplayName("readDouble reads IEEE 754 double")
   void readDoubleBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeDouble(2.71828));
      ByteInput bi = new ByteInput(data);
      assertEquals(2.71828, bi.readDouble(), 0.00001);
   }

   // --- readUTF ---

   @Test
   @DisplayName("readUTF reads modified UTF-8 string")
   void readUTFBasic() throws IOException {
      byte[] data = writeData(dos -> dos.writeUTF("hello world"));
      ByteInput bi = new ByteInput(data);
      assertEquals("hello world", bi.readUTF());
   }

   @Test
   @DisplayName("readUTF reads empty string")
   void readUTFEmpty() throws IOException {
      byte[] data = writeData(dos -> dos.writeUTF(""));
      ByteInput bi = new ByteInput(data);
      assertEquals("", bi.readUTF());
   }

   @Test
   @DisplayName("readUTF reads string with non-ASCII characters")
   void readUTFNonAscii() throws IOException {
      byte[] data = writeData(dos -> dos.writeUTF("\u00e9\u00e8\u00ea"));
      ByteInput bi = new ByteInput(data);
      assertEquals("\u00e9\u00e8\u00ea", bi.readUTF());
   }

   // --- readFully ---

   @Test
   @DisplayName("readFully copies bytes into target array")
   void readFullyBasic() throws IOException {
      byte[] src = {10, 20, 30, 40, 50};
      ByteInput bi = new ByteInput(src);
      byte[] dest = new byte[3];
      bi.readFully(dest);
      assertArrayEquals(new byte[]{10, 20, 30}, dest);
   }

   @Test
   @DisplayName("readFully throws on insufficient data")
   void readFullyThrowsOnOverflow() {
      byte[] src = {10, 20};
      ByteInput bi = new ByteInput(src);
      byte[] dest = new byte[5];
      assertThrows(ArrayIndexOutOfBoundsException.class,
         () -> bi.readFully(dest));
   }

   // --- skipBytes ---

   @Test
   @DisplayName("skipBytes advances offset and returns count")
   void skipBytesBasic() throws IOException {
      byte[] data = writeData(dos -> {
         dos.writeInt(111);
         dos.writeInt(222);
      });
      ByteInput bi = new ByteInput(data);
      int skipped = bi.skipBytes(4);
      assertEquals(4, skipped);
      assertEquals(222, bi.readInt());
   }

   @Test
   @DisplayName("skipBytes clamps to available bytes")
   void skipBytesClamped() {
      byte[] data = {1, 2, 3};
      ByteInput bi = new ByteInput(data);
      int skipped = bi.skipBytes(100);
      assertEquals(3, skipped);
   }

   // --- seek / available ---

   @Test
   @DisplayName("seek sets offset, available reports remaining")
   void seekAndAvailable() {
      byte[] data = new byte[20];
      ByteInput bi = new ByteInput(data);
      assertEquals(20, bi.available());
      bi.seek(5);
      assertEquals(15, bi.available());
      assertEquals(5, bi.getOffset());
   }

   @Test
   @DisplayName("seek to negative offset throws")
   void seekNegativeThrows() {
      byte[] data = new byte[10];
      ByteInput bi = new ByteInput(data);
      assertThrows(ArrayIndexOutOfBoundsException.class,
         () -> bi.seek(-1));
   }

   @Test
   @DisplayName("seek past limit throws")
   void seekPastLimitThrows() {
      byte[] data = new byte[10];
      ByteInput bi = new ByteInput(data);
      assertThrows(ArrayIndexOutOfBoundsException.class,
         () -> bi.seek(11));
   }

   // --- slice constructor ---

   @Test
   @DisplayName("slice constructor creates view of parent ByteInput")
   void sliceConstructor() throws IOException {
      byte[] data = writeData(dos -> {
         dos.writeInt(100);
         dos.writeInt(200);
         dos.writeInt(300);
      });
      ByteInput parent = new ByteInput(data);
      parent.skipBytes(4); // skip first int
      ByteInput slice = new ByteInput(parent, 4); // take second int
      assertEquals(200, slice.readInt());
      // parent offset advanced past the slice
      assertEquals(300, parent.readInt());
   }

   @Test
   @DisplayName("slice constructor throws when length exceeds parent")
   void sliceOverflow() {
      byte[] data = new byte[4];
      ByteInput parent = new ByteInput(data);
      assertThrows(ArrayIndexOutOfBoundsException.class,
         () -> new ByteInput(parent, 10));
   }

   // --- multiple reads in sequence ---

   @Test
   @DisplayName("sequential reads consume data correctly")
   void sequentialReads() throws IOException {
      byte[] data = writeData(dos -> {
         dos.writeInt(42);
         dos.writeShort(7);
         dos.writeUTF("abc");
         dos.writeByte(99);
      });
      ByteInput bi = new ByteInput(data);
      assertEquals(42, bi.readInt());
      assertEquals((short) 7, bi.readShort());
      assertEquals("abc", bi.readUTF());
      assertEquals((byte) 99, bi.readByte());
      assertEquals(0, bi.available());
   }

   // --- bounds checking ---

   @Test
   @DisplayName("readInt throws on insufficient data")
   void readIntBoundsCheck() {
      byte[] data = {1, 2}; // only 2 bytes, need 4
      ByteInput bi = new ByteInput(data);
      assertThrows(ArrayIndexOutOfBoundsException.class, bi::readInt);
   }

   @Test
   @DisplayName("readByte throws on empty input")
   void readByteEmpty() {
      byte[] data = {};
      ByteInput bi = new ByteInput(data);
      assertThrows(ArrayIndexOutOfBoundsException.class, bi::readByte);
   }

   // --- toString ---

   @Test
   @DisplayName("toString includes buf size and hex dump")
   void toStringFormat() {
      byte[] data = {1, 2, 3};
      ByteInput bi = new ByteInput(data);
      String str = bi.toString();
      assertTrue(str.contains("(3)"), "should contain buffer size");
   }
}
