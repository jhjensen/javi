package javi;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;

class EditGroup extends Rgroup {
/* Copyright 1996 James Jensen all rights reserved */
static final String copyright = "Copyright 1996 James Jensen";

private int dotcommand;
private char dotbufid;
private KeyEvent dotevent2;
private KeyEvent dotevent3;
private int dotcount= 1;
private int dotrcount= 0;
private char dotchar;
private Object dotarg;

MapEvent evhandler;
InsertBuffer icontext;

EditGroup(MapEvent evhandleri) {
   final String[] rnames = {
     "",
     "insert",
     "Insert",
     "append",
     "Append",
     "openline",     //5
     "Openline",
     "substitute",
     "Substitute",
     "deletechars",
     "deletetoend",  //10
     "deletetoendi",
     "deletemode",
     "joinlines",
     "subchar",
     "changecase",   //15
     "changemode",
     "putbefore",
     "putafter",
     "qmode",
     "yankmode",     //20
     "yank",
     "doover",
     "markmode",
     "egunused1",
     "egunused2",           //25
     "shiftmode",
     "tabfix"
   };

  register(rnames);
  icontext = new InsertBuffer(this);
  evhandler = evhandleri;
}

public Object doroutine(int rnum,Object arg,int count,int rcount,FvContext  fvc ,
     boolean dotmode) 
   throws InterruptedException,IOException,InputException,ReadOnlyException {
   //trace("rnum = " + rnum + " count = " + count + " rcount = " + rcount);
   if (!dotmode && !(rnum >=20 && rnum <=22)) {
      dotcommand=rnum;
      dotcount=count;
      dotrcount=rcount;
      dotarg = arg;
      dotrcount= rcount;
   }

   switch (rnum) {
      case 1: {
             boolean[] a = (boolean[]) arg;
             icontext.insertmode(dotmode,count,fvc,a[0],a[1]); 
           break;
            }
      case 2: fvc.cursorxabs(0); 
             icontext.insertmode(dotmode,count,fvc,false,false); 
           break;
      case 3: fvc.cursorx(1); 
            icontext.insertmode(dotmode,count,fvc,false,false); 
           break; 
      case 4: fvc.cursorxabs(Integer.MAX_VALUE);
           icontext.insertmode(dotmode,count,fvc,false,false); 
           break;
      case 5: 
          fvc.cursorxabs(Integer.MAX_VALUE);
          fvc.inserttext("\n");
          fvc.cursory(1);
          icontext.insertmode(dotmode,count,fvc,false,false);
          break;
      case 6:
          fvc.cursorxabs(0);
          fvc.inserttext("\n");

          icontext.insertmode(dotmode,count,fvc,false,false);
          break;
      case 7: substitute(dotmode,count,fvc);
          break;
      case 8: ucSubstitute(dotmode,count,fvc);
          break;
      case 9: {
             boolean[] a = (boolean[]) arg;
             deleteChars('0',fvc,a[0],a[1],count);
             break;
            }
      case 10:  deletetoend('0',count,fvc); 
           break;
      case 11:  deletetoend('0',count,fvc);
                icontext.insertmode(dotmode,count,fvc,false,false); 
           break;
      case 12:  deletemode('0',dotmode,count,rcount,fvc);
           break;
      case 13:  
           fvc.cursorxabs(fvc.edvec.joinlines(count,fvc.inserty()));
           break;
      case 14:  subChar(dotmode,count,fvc); 
           break;
      case 15: fvc.edvec.changecase(
             fvc.insertx(),fvc.inserty(),fvc.insertx() + count,fvc.inserty());
             fvc.cursorx(count);
           break;
      case 16:  changemode('0',dotmode,count,rcount,fvc);
           break;
      case 17:  putbuffer('0',false,fvc);
           break;
      case 18:  putbuffer('0',true,fvc); 
           break;
      case 19:  qmode(count,rcount,dotmode,fvc) ; 
           break;
      case 20:  yankmode('0',false,count,rcount,fvc);
           break;
      case 21:  
            ArrayList <String> bufs = fvc.getElementsAt(count);
            //trace("yank " + count + " lines ");
            Buffers.deleted('0',bufs);
            break;
      case 22: 
           if (dotcommand!=0) {
              if (rcount==0)
                 count= dotcount;
              dotcount=count;
              return doroutine(dotcommand,dotarg,dotcount,dotrcount,fvc, true);
           }
           return null;
      case 23: 
             markmode('0',dotmode,count,rcount,fvc, ((Integer)arg).intValue()==1);break;
      case 24: 
      case 25: 
             return null;

      case 26: shiftmode(((Integer)arg).intValue(),count,fvc,dotmode,rcount);break;

      case 27:
         fvc.edvec.tabfix(fvc.vi.getTabStop());
         break;
      default:
          throw new RuntimeException();
   }
   fvc.edvec.checkpoint();
   fvc.fixCursor();
   return null;
}

private void shiftmode(int direction,int count ,FvContext fvc,
    boolean dotmode,int rcount) 
       throws ReadOnlyException,InterruptedException,IOException,InputException {
   if (!dotmode)
      dotevent3= EventQueue.nextKeye(fvc.vi);
   KeyEvent event =dotevent3;
     
   switch(event.getKeyChar()) { 
      case '<':
              fvc.cursorx(fvc.edvec.shiftleft(fvc.inserty(),count));
              break;
      case '>':
              fvc.cursorx(fvc.edvec.shiftright(fvc.inserty(),count));
              break;
      case 27: // esc 
         break;
      default:
              int yold = fvc.inserty();
              int starty,amount;
              if (!evhandler.domovement(event,count,rcount,dotmode,fvc))
                 break;
              if (yold < fvc.inserty()) {
                  starty=yold;
                  amount=fvc.inserty()-yold+1;
              } else  {
                  starty=fvc.inserty();
                  amount=yold-starty+1;
              }
              if (direction !=1)
                 fvc.cursorx(fvc.edvec.shiftleft(starty,amount));
              else
                 fvc.cursorx(fvc.edvec.shiftright(starty,amount));
              break;
   }
}

private int donex,markamount;
private void markmode(char bufid,boolean dotmode,int count,int rcount,FvContext fvc,
              boolean VMode) 
         throws  ReadOnlyException,InputException,IOException {
     int starty,startx, doney;
     int xold=0;
     int yold=0;
     if (!dotmode) {
         Position markpos = fvc.vi.getMark();
         if (markpos==null) {
            if (!VMode)
               xold = fvc.insertx();
            yold = fvc.inserty();
            fvc.vi.setMark(fvc.getPosition("mark position"));
         } else {
           xold=markpos.x;
           yold=markpos.y;
         }
     }
out: do {
        KeyEvent event;
        if (!dotmode) {
           try {while ( evhandler.domovement(event = 
                      EventQueue.nextKeye(fvc.vi),count,rcount,dotmode,fvc)) {/* intentionaly empty*/}
           } catch (InterruptedException e ) {
              trace("markmode caught " + e);
              break out;
           }
         
           dotevent3=event;
           if (VMode)
               fvc.cursorxabs(Integer.MAX_VALUE);
           if (yold < fvc.inserty()) {
               starty=yold;
               startx=xold;
               donex=fvc.insertx();
               doney=fvc.inserty();
               markamount=fvc.inserty()-yold+1;
           } else  {
               starty=fvc.inserty();
               startx=fvc.insertx();
               donex=xold;
               doney=yold;
               markamount=yold-starty+1;
               if ((yold == fvc.inserty()) && (donex < startx)) {
                        int temp = startx;
                        startx=donex;
                        donex=temp;
               }
           }
        } else {
            bufid = dotbufid;
            event = dotevent3;
            starty=fvc.inserty();
            startx=fvc.insertx();
            doney=starty+markamount-1;
            if (VMode) {
               donex= Integer.MAX_VALUE;
               startx = 0;
            } else {
               if (markamount==1)
                  donex=startx+donex;
               startx=fvc.insertx();
            }
        }
        char key = event.getKeyChar();
        try { switch(key) { 
           case 'o':
              Position markpos = fvc.vi.getMark();
              xold=fvc.insertx();
              yold=fvc.inserty();
              fvc.vi.setMark(fvc.getPosition("mark position"));
              fvc.cursorabs(markpos);
              continue;
           case 'd':
              deletetext(bufid,fvc,false,startx,starty,donex,doney);
              break out;
           case 'y':
              deletetext(bufid,fvc,true,startx,starty,donex,doney);
              break out;
           case 'v':
           case 'V':
           case 27: // esc 
              break out;
           case 'Y': 
              Buffers.deleted(bufid,fvc.edvec.getElementsAt(starty,markamount));
              break out;
           case 'D':
              if (!fvc.edvec.contains(starty+markamount-1))
                  markamount = fvc.edvec.finish()-1;
              Buffers.deleted(bufid,fvc.edvec.remove( starty,markamount));
              fvc.edvec.checkpoint();
              fvc.fixCursor();
              break out;
           case '~':
                 fvc.edvec.changecase(startx,starty,donex,doney);
              break out;
           case 'J':
              fvc.cursorabs(fvc.edvec.joinlines(markamount,starty),
                   starty);
              break out;
           case '<':
              fvc.cursorx(fvc.edvec.shiftright(starty,markamount));
              break out;
           case '>':
              fvc.cursorx(fvc.edvec.shiftleft(starty,markamount));
              break out;
           case 'S':
           case 's':
              fvc.cursorabs(startx,starty);
              String line=fvc.edvec.gettext(startx,starty,donex,doney);
              MoveGroup.dosearch(key=='S',1,fvc,line);
              break out;
           case 12:
              MiscCommands.queueRedraw(true);
              continue;
           case 29:
              line=fvc.edvec.gettext(startx,starty,donex,doney);
              try {
                 Rgroup.doroutine("gototag",line,1,1,fvc, false);
              } catch (IOException e) {
                 throw new RuntimeException("editgroup.markmode got unexpected " ,e);
              } catch (InterruptedException e) { // Intentionally empty
              }

              break out;
           default:
              continue;
        }
        } catch (ReadOnlyException e ) {
           fvc.vi.clearMark();
           throw e;
        } 
     } while (!dotmode);
     fvc.vi.clearMark();
}
private void qmode(int count,int rcount,boolean dotmode,FvContext fvc) 
   throws InterruptedException,IOException,ReadOnlyException,InputException {
    
     KeyEvent event;
     char bufid;

     if (!dotmode) {
            bufid = (EventQueue.nextKeye(fvc.vi).getKeyChar());
            event = EventQueue.nextKeye(fvc.vi);
     } else {
            bufid = dotbufid;
            event = dotevent2;
     }
     switch(event.getKeyChar()) { 
         case 'p':
             if (dotmode && bufid >= '0' && bufid <='8')
                 bufid++;
             putbuffer(bufid,true,fvc);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'P':
             putbuffer(bufid,false,fvc);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'd':
             deletemode(bufid,dotmode,count,rcount,fvc);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'y':
             yankmode(bufid,dotmode,count,rcount,fvc);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'c':
             changemode(bufid,dotmode,count,rcount,fvc);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'X' :
         case 127 :
             deleteChars(bufid,fvc,false,false,count);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'x' :
             deleteChars(bufid,fvc,true,true,count);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'D' :
             deletetoend(bufid,count,fvc);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'C' :
             deletetoend(bufid,count,fvc);
             icontext.insertmode(dotmode,count,fvc,false,false);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'Y' :
             Buffers.deleted(bufid,fvc.getElementsAt(count));
             dotbufid=bufid;
             dotevent2=event;
            break;
         case 'v' :
             markmode(bufid,dotmode,count,rcount,fvc,false);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 'V' :
             markmode(bufid,dotmode,count,rcount,fvc,true);
             dotbufid=bufid;
             dotevent2=event;
             break;
         case 27: //escape
             break;
     }
}

static void appendCurrBuf(StringBuilder sb ,boolean singleline) {
   Object obj = Buffers.getbuf('0');
   if (obj!=null)  {
      if (obj instanceof ArrayList) {
         for (Object obj1: (ArrayList)obj)  {
            sb.append(obj1.toString());
            sb.append(singleline ? ' ': '\n');
         }
      } else
         sb.append(obj.toString());
   }
}

private void putbuffer(char id, boolean after,FvContext fvc)  throws ReadOnlyException{
    

   Object buf =Buffers.getbuf(id);
   if (buf==null)
      return;

   if (buf instanceof String) {
     if (after)
         fvc.cursorx(1);
     fvc.cursorabs(fvc.inserttext((String)buf)); 
   } else {
       ArrayList<String> buf2 = (ArrayList<String>)buf;
       fvc.insertStrings(buf2,after);
       fvc.cursory(buf2.size());
   }
}

private void substitute(boolean dotmode,int count, FvContext fvc) 
 throws ReadOnlyException,InputException ,IOException{
    deleteChars('0',fvc,true,true,count);
    count=1;
    icontext.insertmode(dotmode,count,fvc,false,false);
}
 
private void ucSubstitute(boolean dotmode,int count, FvContext fvc)  
    throws ReadOnlyException,InputException,IOException{
    MoveGroup.starttext(fvc);
    deletetoend('0',count,fvc);
    count= 1;
    icontext.insertmode(dotmode,count,fvc,false,false);
    fvc.edvec.checkpoint();
    fvc.fixCursor();
}
int findspacebound(FvContext fvc,int linepos) {
   int j;
   int lineno;
   
   for (lineno=fvc.inserty()-1;lineno>0;lineno--) {
       String line =  fvc.at(lineno).toString();
       // skip non spaces
          for (j = linepos;j<line.length();j++)
            if (line.charAt(j) == ' ')
                break;
          // skip spaces
          for (;j<line.length();j++) 
            if (line.charAt(j) != ' ')
                break;
          if (j<line.length()) {  // found good line
               return j - linepos;
          }
   }
   return 0;
}

static void deleteChars(char bufid,FvContext fvc,boolean reversable,boolean forward,int count) throws ReadOnlyException {
   String line = fvc.at().toString();
   String deleted = null;


   //Thread.dumpStack();
   
   //trace("count = " + count + " llen = " + line.length());
   
   if (line.length()==fvc.insertx()&& reversable)
       forward=false;
   if (forward) {
         if (fvc.insertx()+count>line.length())
                  count = line.length() - fvc.insertx();
         deleted = line.substring(fvc.insertx(),fvc.insertx()+count);
         line = line.substring(0,fvc.insertx()) + 
               line.substring(fvc.insertx()+count,line.length());
   } else {
         if (fvc.insertx()<count)
         count=fvc.insertx();
         if  (0 == count)
                return;
         deleted = line.substring(fvc.insertx()-count,fvc.insertx());
         line = line.substring(0,fvc.insertx()-count) + 
                line.substring(fvc.insertx(),line.length());
         fvc.cursorx(-count);
   }
   Buffers.deleted(bufid,deleted);
   fvc.changeElementStr(line);
   //trace("count = " + count + " llen = " + line.length());
   return;
}

static void deletetoend(char bufid,int count,FvContext fvc) throws ReadOnlyException {
   int cy = fvc.inserty();
   int lastline = fvc.inserty()-1 +count;
   deletetext(bufid,fvc,false,fvc.insertx(),fvc.inserty(),
       fvc.at(lastline).toString().length(),lastline);
   fvc.cursory(cy -fvc.inserty());
}

static private void deletetext(char bufid,FvContext fvc,
        boolean preserve,int xstart,int ystart, int xend,int yend)
       throws ReadOnlyException {
   Buffers.deleted(bufid,fvc.edvec.deletetext(preserve,xstart,ystart,xend,yend));
}

private void deletemode(char bufid,boolean dotmode,int count,int rcount,FvContext fvc) 
              throws InterruptedException,ReadOnlyException,IOException,InputException {
//trace("count = " + count + " rcount = " + rcount
//    +  " fvc = " + fvc);
     KeyEvent event;
     int xold = fvc.insertx();
     int yold = fvc.inserty();

     if (!dotmode) {
            event = EventQueue.nextKeye(fvc.vi);
            dotevent3=event;
     } else
            event=dotevent3;
     
     switch(event.getKeyChar()) {

         case 'd':
             if (!fvc.edvec.contains(fvc.inserty()+count-1))
                 count= fvc.edvec.finish()-1;
             Buffers.deleted(bufid, fvc.edvec.remove( fvc.inserty(),count));
             fvc.edvec.checkpoint();
             fvc.fixCursor();
             return;

        default:
             evhandler.domovement(event,count,rcount,dotmode,fvc);
             if (yold>fvc.inserty()|| (yold==fvc.inserty()&& 
                  xold > fvc.insertx())) 
                 deletetext(bufid,fvc,false,fvc.insertx(),fvc.inserty(),xold,yold);
             else {
                 deletetext(bufid,fvc,false,xold,yold,fvc.insertx(),fvc.inserty());
                 fvc.cursorabs(xold,yold) ;
             }
             return;
     }
                
}
private void yankmode(char bufid,boolean dotmode,int count,int rcount,FvContext fvc
           ) throws InterruptedException,ReadOnlyException,IOException,InputException {
//trace("count = " + count + " rcount = " + rcount);
     KeyEvent event;
     int xold = fvc.insertx();
     int yold = fvc.inserty();

     if (!dotmode) {
            event = EventQueue.nextKeye(fvc.vi);
            dotevent3=event;
     } else
            event=dotevent3;

     switch(event.getKeyChar()) {

         case 'y':
             if (!fvc.edvec.containsNow(fvc.inserty()+count-1))
                 count= fvc.edvec.finish()-1;
             Buffers.deleted(bufid,fvc.getElementsAt(count));
             return;

     }
     Position save = fvc.getPosition("yankmark");
     evhandler.domovement(event,count,rcount,dotmode,fvc);
     if (yold>fvc.inserty()|| (yold==fvc.inserty()&& 
        xold > fvc.insertx())) 
         deletetext(bufid,fvc,true,fvc.insertx(),fvc.inserty(),xold,yold);
     else {
         deletetext(bufid,fvc,true,xold,yold,fvc.insertx(),fvc.inserty());
     }
     fvc.cursorabs(save);
}
private void changemode(char bufid,boolean dotmode,int count,int rcount, FvContext fvc) 
     throws InterruptedException,ReadOnlyException,InputException,IOException{

   KeyEvent event;
   if (!dotmode) 
      event = EventQueue.nextKeye(fvc.vi);
   else
      event = dotevent3;

   switch (event.getKeyChar())  {
          case 'c':
               dotevent3=event;
               MoveGroup.starttext(fvc);
               deletetoend(bufid,count,fvc);
               icontext.insertmode(dotmode,count,fvc,false,false);
               return;
          case 27: //esc
             return;
      }
   if (!dotmode)
        EventQueue.pushback(event);

   deletemode(bufid,dotmode,count,rcount,fvc);
   icontext.insertmode(dotmode,1,fvc,false,false);
}

private void subChar(boolean dotmode,int count,FvContext fvc) throws ReadOnlyException,ExitException {

    if (!dotmode)
        while ((dotchar = EventQueue.nextKey(fvc.vi)) == KeyEvent.CHAR_UNDEFINED)
           ;
    if (dotchar==27)
       return;
    String line = fvc.at().toString();
    //trace("count " + count + " line.length() " + line.length() + " insertx " + fvc.insertx());
    StringBuilder istring = new StringBuilder(line.substring(0,fvc.insertx()));
    int icount = line.length() - fvc.insertx() ;
    icount = icount < count
       ? icount
       : count;
    for (int ii = icount ; ii>0;ii--) 
       istring.append(dotchar);
    istring.append(line.substring(fvc.insertx()+icount,line.length()));
    fvc.changeElementStr(istring.toString());
}


}
