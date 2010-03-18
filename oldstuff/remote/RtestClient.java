package javi;

import java.rmi.*;
import java.io.Serializable;
//import java.rmi.Nameing;
public class RtestClient {

  static class RtestExample implements Task,Serializable {
     static final long serialVersionUID=0;
     public void execute() {
        System.out.println("test reached");
     }
  }

  public static void main(String[] args) {
    String rmiURL = "rmi://speedy/Compute";
    float payBack = 0f;
    try {
      //Get an instance of the class:ta startcmd

      Compute ic = (Compute)Naming.lookup(rmiURL);
      ic.executeTask(new RtestExample());
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
    } // try catch
  } //main
} //class
