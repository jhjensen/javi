package javi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Compute extends Remote {
     void executeTask(Task t) throws RemoteException;
}

