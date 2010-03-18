package javi;

import javax.swing.JEditorPane;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import java.awt.Dimension;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.JScrollPane;
import java.io.IOException;

class htmlv extends JScrollPane implements HyperlinkListener {
       
//   JEditorPane jp = new  JEditorPane();
   htmlv(String page) throws IOException {
      super(new  JEditorPane());
      JEditorPane jp = (JEditorPane)getViewport().getView();
      jp.setPage(page);
      jp.setEditable(false);
//      JScrollPane scrollPane = new JScrollPane(jp);
//      add(scrollPane, BorderLayout.CENTER);
      jp.addHyperlinkListener(this);
      StyleSheet st = ((HTMLEditorKit)jp.getEditorKit()).getStyleSheet();
      st.addRule("body { font-size: 12pt }");
      //st.setBaseFontSize("+2");
      //st.setBaseFontSize(0);
      //setSize(550,550);
      Dimension min = new Dimension(520,520);
      setPreferredSize(min);
//      jp.setPreferredSize(new Dimension(500,500));
//      setPreferredSize(new Dimension(550,550));
//      jp.setSize(500,500);
    
//      doLayout();
      //((HTMLEditorKit)jp.getEditorKit()).setStyleSheet(st);
   }
   public void hyperlinkUpdate(HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) 
         try {
           ((JEditorPane)getViewport().getView()).setPage(event.getURL());
         } catch(IOException ioe) {
           // Some warning to user
         }
    }
/*
public Dimension getPreferredSize() {
//   Dimension d = new Dimension(pixelWidth,screenSize*charheight);
//Thread.dumpStack();
   trace("" );
   return super.getPreferredSize();
}
public void setSize(int x,int y) {
   trace("reached setSize " + x + "," + y);
}
*/
/*
          setEnabled(true);
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
public void processEvent(AWTEvent ev) {
 trace("ev " + ev.getID() + "  has focus " + hasFocus()  );
 super.processEvent(ev);
}
public void processKeyEvent(KeyEvent ev) {
 trace("key ev " + ev + "  has focus " + hasFocus()  );
 super.processKeyEvent(ev);
}

protected boolean processKeyBinding(KeyStroke ks,
                                    KeyEvent e,
                                    int condition,
                                    boolean pressed) {
 trace("processKeyBinding " + e);
 return super.processKeyBinding(ks,e,condition,pressed);
}
*/
static void trace(String str) {
   ui.trace(str,1);
}
}
