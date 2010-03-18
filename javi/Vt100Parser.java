package javi;
import java.io.IOException;
import java.io.BufferedInputStream;

class Vt100Parser extends EventQueue.IEvent implements Runnable {

   private int state= NORM;
   private final static int NORM =0;
   private final static int ESC =1;
   private final static int GETNUM =2;
   private final static int MODE =3;
 //  private final static int MODEND =4;
   private final static int OSCMODE =5;
   private final static int OSCMODE2 =6;
   private final static int OSCMODE3 =7;
  // private final static int GETTITLE =8;
   private final static int CR =9;
   private final Vt100 window;
   private final StringBuffer sb=new StringBuffer();
   private int[] numacc=null;
   private int currnumacc;
   private int highestSet;
   private int modenumber;
   private char oscmode;
   private final StringBuffer oscstring = new StringBuffer();
   private final BufferedInputStream input;
   int recbyte;
   Thread rthread = new Thread(this);
//   private CharFifo fifo=new CharFifo();
   
   Vt100Parser(Vt100 win,BufferedInputStream ins) {
      window = win;
      input = ins;
      rthread.start();
   }

   public void run() {
      try {
         while (true) {
            synchronized (this) {
               recbyte = input.read();
               EventQueue.insert(this);
               wait(10000);
            }
         } 
      } catch (Throwable e) {
         UI.popError("Vt100 caught IOError",e);
      }
   }

   private final void caseCR(int inc) {
             sb.setLength(sb.length()-1);    
             sb.append((char)inc);    
             state=NORM;
             if (inc=='\n')
                return;
            else
               window.setX(1,sb);  
            caseNORM(inc);
   }

   private final void caseNORM(int inc) {
             switch (inc) {
                //case '\t':
                //   sbprocess();
                //  window.insertTab();
                //  break;
                case  27:
                   state = ESC;
                   break;
                case 0: //ignored
                case '\177': //ignored
                   break;
                case 7:
                   trace("ignored bell char"); //???
                   break;
                case '\b': //??? sbprocess
                   window.incX(-1,sb);
                   break;
                case '\r': // may want to just move to beginning of line - vt100 spec

                    //trace("!!!! flush");
                    //sbprocess();
                    //window.setX(1);  
                    sb.append((char)inc);
                    state = CR;
                   break;
                case 15:
                   trace1("receive SI character select character set");
                   break;
                case 12:
                case 11:
                case 10:
                case 9:
                   sb.append((char)inc);
                   break;
                default:
                   if (inc<20)
                      trace1("unhandeld control character 0x"+ Integer.toHexString(inc));
                   if (inc>0x7f)
                     trace(this + "questionable char " + Integer.toHexString(inc));
                   //fvc.cursoryabs(ev.readIn()-1);
                   //fvc.cursorxabs(Integer.MAX_VALUE);
                   //editgroup.deleteChars('\0',fvc,false,true,1);
                   sb.append((char)inc);
             }
   }

   private final void caseGETNUM(int inc) {
             if (inc >='0' && inc <='9') {
                 numacc[currnumacc] = numacc[currnumacc]*10 + inc-'0';
                 highestSet = currnumacc;
             } else {
                int newstate = NORM;
                boolean def = highestSet != currnumacc;
                switch (inc) {
                   case 'A':
                      trace1("cursor up " + numacc[currnumacc]);
                      window.incY(-1 * (def ? 1 :  numacc[currnumacc]),sb);
                      break;
                   case 'B':
                      trace1("cursor down " + numacc[currnumacc]);
                      window.incY(1* (def ? 1 :  numacc[currnumacc]),sb);
                      break;
                   case 'C':
                      trace1("cursor right " + numacc[currnumacc]);
                      window.incX(1* (def ? 1 :  numacc[currnumacc]),sb);
                      break;
                   case 'D':
                      trace1("cursor left " + numacc[currnumacc]);
                      window.incX(-1* (def ? 1 :  numacc[currnumacc]),sb);
                      break;
                   case 'H':
                      
                      switch (currnumacc + (def ? 0 :1)) {
                         case 0: 
                             trace1("move to homei??: " );
                             window.setXY(1,1,sb );
                             break;
                         case 1: 
                             trace1("move to setY " + numacc[0] );
                             window.setXY(1,numacc[0],sb);
                             break;
                         case 2:
                            trace1("move XY( " + numacc[1] + "," + numacc[0] +")");
                            window.setXY(numacc[1],numacc[0],sb);
                            break;
                         default :
                            trace("bad number accumulated: " +  currnumacc);
                      }
                      break;
                   case 'J':
                      switch (numacc[currnumacc]) {
                         case 0:
                            trace1("erase from pos to end of screen");
                            break;
                         case 1:
                            trace1("erase from start of screen to pos");
                            break;
                         case 2:
                            window.eraseScreen(sb);
                            trace1("erase entire screen ");
                            break;
                         default:
                            trace1("unknown [J index " + numacc[currnumacc]);
                       }
                        break;  
                   case 'K':
                      switch (numacc[currnumacc]) {
                         case 0 : 
                            trace1("erase from cursor to end of line");
                            window.eraseToEnd(sb);
                            break;
                         case 1 : 
                            trace1("erase from beginning of line to cursor ???");
                            break;
                         case 2 : 
                            trace1("erase entire line at cursor ");
                            window.eraseLine(sb);
                            break;
                         default :
                            trace1("unexpected erase character");
                      }
                         
                      break;
                   case 'P': // from xterm doc erase number of characters
                      window.eraseChars(def ? 1 : numacc[currnumacc],sb);
                      break;
                   case 'm': // Character Attributes
                                  // capturing plain text so ignore
                         trace1("[m (graphic Rendition - bold etc) unimplementd");
                      break;
                   case ';':
                      if (currnumacc<= numacc.length-1) {
                         int[] temp = new int[numacc.length+1];
                         System.arraycopy(numacc,0,temp,0,numacc.length);
                         numacc=temp;
                      }
                      //trace("accumulate new number " + numacc[currnumacc]);
                      currnumacc++;
                      numacc[currnumacc]=0; 
                      newstate = state;
                      break;
                      
                    case '?':
                       newstate = MODE;
                       modenumber=0;
                       break ;
                   case 27:
                      newstate = ESC;
                      break ; 
                   case '@':
                      for (int i =0;i<=numacc[currnumacc];i++)
                         sb.append(' ');
                      break;
                   case 'h': //set mode xterm
                   case 'l': //reset mode xterm
                      
                      boolean val = inc == 'h';
                      for (int i =0;i<=currnumacc;i++)
                         switch (numacc[currnumacc]) {
                            case 4: 
                               window.setInsertMode(val,sb);
                               break;
                            default:
                               trace("unknown mode " + numacc[currnumacc]);
                         }
                      break;
                   
                   case 'r': //	Change Attributes in Rectangular Area (DECCARA).
                                //P t ; P l ; P b ; P r denotes the rectangle.
                                //P s denotes the SGR attributes to change: 0, 1, 4, 5, 7
                       trace1("receive DECCARA count = " + currnumacc);
                       break;
                   case 'L': // insert lines before or after???
                      window.insertLines(0== numacc[currnumacc] ? 1 : numacc[currnumacc],sb);
                      break;
                   default:
                      trace("unkown [ terminator " + (char)inc + " decimal "  + inc+ " 0x" + Integer.toHexString(inc));
                }
                //trace("completed [" + (char) inc);
                state=newstate;
             }
   }
   private final void caseMODE(int inc) {
             if (inc >='0' && inc <='9')
                 modenumber = modenumber*10 + inc-'0';
             else {
                int val = inc == 'h' ? 1 :0;
               
                switch (modenumber) {
                   case 1:
                      break;
                   case 2:
                      trace("vt52 mode shouldn't happen");
                      break;
                   case 3:
                      trace("132 column mode ???");
                      break;
                   case 4:
                      trace("smooth scrolling ??? inc = " + inc);
                      break;
                    case 47:
                       trace("??? use alternate screen buffer");
                       break;
                   
                   default:
                      trace ("setting unknown mode " + (char)modenumber + " decimal "  + modenumber+ " 0x" + Integer.toHexString(modenumber) + " to " + val);
                }
                state=NORM;
             }
   }
   private final void caseOSCMODE3(int inc) {
             if (inc ==7) {
                state = NORM;
                oscstring.setLength(0);
                switch (oscmode) {
                   case '0':
                      trace("change icon and title :" + oscstring);
                      break;
                   case '1':
                      trace("change icon name:" + oscstring);
                      break;
                   case '2':
                      trace("change window name:" + oscstring);
                      break;
                   case '4':
                      trace("change log file :" + oscstring);
                      break;
                   default:
                      trace("unexpected oscmode " + oscmode);
                }
             } else 
                oscstring.append(inc);
   }

    private final void caseESC(int inc) {
          
             switch (inc) {
                case '[': //91
                  state=GETNUM;
                  numacc=new int[1];
                  numacc[0]=0;
                  currnumacc=0;
                   
                   break;
                case '7': // save cursor and attributes
                   window.saveCursor(sb);
                   state=NORM;
                   break;
                case '8': // restore cursor and attributes
                   window.restoreCursor(sb);
                   break;
                case 'M':
                      trace1 ("reverse index, whatever that means");
                      window.incY(-1,sb);
                       state=NORM;
                   break;
                   
                case ' ':
                   trace("what does escape ' ' mean?");
                   state = NORM;
                   break;
                
                case 27:
                   trace("what does double escape mean?");
                    // I think it means stay in escape
                   break;
                case '>':
                   trace1("keypad numeric mode");
                   state = NORM;
                   break;
                case '=':
                   trace1("exit keypad numeric mode");
                   state = NORM;
                   break;
                case ']':
                   state = OSCMODE;
                   break;
                default:
                   trace("unhandled escape code " + (char)inc + " decimal "  + inc+ " 0x" + Integer.toHexString(inc));
                   state = NORM;
               }
   }

    private final void caseOSCMODE2(int inc) {
             if (inc!= ';')
                 trace ("unexpected OSCMODE2 character");
             state = OSCMODE3;
   }

   private final void caseOSCMODE(int inc) {
             oscmode = (char)inc;
             state = OSCMODE2;
   }

   private final void doChar(int inc) throws InputException {
      if (inc ==-1)
         throw new InputException("end of input for Vt100");
      switch(state)  {
         case CR:
            caseCR(inc);
            break;
         case NORM:
            caseNORM(inc);
            break;
         case GETNUM:
            caseGETNUM(inc);
            break;
         case MODE:
            caseMODE(inc);
            break ;
          case OSCMODE3:
            caseOSCMODE3(inc);
            break;     
         case OSCMODE2:
            caseOSCMODE2(inc);
            break;
         case OSCMODE: 
            caseOSCMODE(inc);
            break;
         case ESC:
            caseESC(inc);
            break;
         default:
            trace("unhandled state = " + state);
      }
   }

   synchronized void execute() {
       
      trace("ParseInput executing on ");
      int inc;
      try { 
         doChar(recbyte);
         while (input.available() != -0)   {
            doChar(input.read());
            //trace("state " + state + " received byte " + (char)inc + " decimal "  + inc+ " 0x" + Integer.toHexString(inc));
         }
         if (state==CR) {
           sb.setLength(sb.length()-1);    
           state=NORM;
           window.setX(1,sb);  
         }
         window.updateScreen(sb);
         notify();
      } catch (InputException e) {
           UI.reportError("failure in VT100 io " + e.toString());
      } catch (Throwable e) {
           UI.popError("failure in VT100 io",e);
      }
   }

   static void trace(String str) {
      Tools.trace(str,1);
   }

   private static final boolean traceflag =false;
   private static void trace1(String str) {
      if (traceflag)
        Tools.trace(str,2);
   }
}
