package org.theanarch.openproxy.Proxy;

import java.net.ServerSocket;
import java.net.Socket;

public class Proxy {

    public Proxy(){
        new java.lang.Thread(new Runnable(){
            private Socket socket;

            @Override
            public void run(){
                try{
                    ServerSocket serverSocket = new ServerSocket(8080);

                    System.out.println("Proxy started on port 8080");

                    while((socket = serverSocket.accept()) != null){
                        (new Thread(socket)).start();
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
