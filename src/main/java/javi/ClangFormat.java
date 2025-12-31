package javi;

import java.io.InputStream;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import static history.Tools.trace;
import static history.Tools.executeIn;
import java.util.Iterator;
import java.util.ArrayList;

public class ClangFormat {

   @SuppressWarnings("unchecked")
   static void format(int startLine, int endLine, TextEdit ex) {
      //trace("format start", startLine, "end", endLine);
      Iterator<String> it = ex.getStringIter();
      StringBuilder docb = new StringBuilder();
      int index = 0;
      int[] map = new int[ex.readIn() - 1];
      for (Object str : ex) {
         //trace("appending", str, "index", index, "offset ", docb.length());
         map[index++] = docb.length();
         docb.append(str.toString());
         docb.append("\n");
      }

      //trace("map", map);
      String doc = docb.toString();
      try {
         NodeList nList = doCheck(doc, startLine, endLine, ex.getName());

         for (int temp = nList.getLength() - 1; temp >= 0; temp--) {
            Node nNode = nList.item(temp);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
               Element eElement = (Element) nNode;
               int offset = Integer.parseInt(eElement.getAttribute("offset"));
               int length = Integer.parseInt(eElement.getAttribute("length"));
               String fromString = doc.substring(offset, offset + length);
               String toString =   eElement.getTextContent();

               //trace("offset", offset, "length", length, "fromString:" + fromString + ":", "toString:" + toString + ":");

               int ystart = map.length - 1;
               for (; ystart >= 0; ystart--) {
                  // trace("ystart", ystart, "map[ystart]", map[ystart]);
                  if (map[ystart] <= offset) {
                     int xstart = offset - map[ystart];
                     ArrayList<String> sar = ex.stringtoarray(fromString);
                     //trace("sar size", sar.size(), "sar:", sar, ":");
                     int yend = sar.size() - 1 + ystart;
                     int xend = sar.get(sar.size() - 1).length();
                     if (yend == ystart)
                        xend += xstart;
                     String extext = ex.deletetext(
                        false, xstart, ystart + 1, xend, yend + 1);

                     //trace("xstart", xstart, "ystart", ystart, "xend", xend,
                     //   "yend", yend, "dtext:" + extext + ":");
                     //trace("new line:" + ex.at(ystart + 1) + ":");

                     if (!fromString.equals(extext)
                         && !(extext == null && fromString.length() == 0))
                        throw new RuntimeException(
                           "unexpected replacement extext:" + extext
                           + ": fromString:" + fromString + ":");
                     ex.inserttext(toString, xstart, ystart + 1);
                     break;
                  }
               }
            }
         }
      } catch (Exception e) {
         UI.reportError("attempt to reformat file failed " + e);
         e.printStackTrace();
      }
   }

   private static DocumentBuilder builder;
   static {
      try {
         builder = DocumentBuilderFactory .newInstance().newDocumentBuilder();
      } catch (Exception ex) {
         trace("DocumentBuilder initialization failure");
      }
   }

   private static NodeList doCheck(String doc, int startLine, int endLine,
      String fileName) throws IOException, org.xml.sax.SAXException {
      InputStream xml = executeIn(doc, "clang-format-mp-20",
         "-lines=" + startLine + ":" + endLine, "-style=file",
         "-assume-filename=" + fileName, "-output-replacements-xml");
      Document xdoc = builder.parse(xml);
      xdoc.getDocumentElement().normalize();
      return xdoc.getElementsByTagName("replacement");
   }
}
