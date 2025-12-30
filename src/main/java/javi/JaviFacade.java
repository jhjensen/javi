package javi;
public final class JaviFacade {

   private JaviFacade() { }

   public static TextEdit<String> createFileTE(String filename) {
      FileProperties<String> fp = new FileProperties(
         FileDescriptor.LocalFile.make("perftest"), StringIoc.converter);
      return new TextEdit<String>(new FileInput(fp), fp);
      //public static Vector<TextEdit<String>> getFileList()  {
      //   return FileList.getFileArray();
   }
}
