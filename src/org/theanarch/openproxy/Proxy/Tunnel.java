package org.theanarch.openproxy.Proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class Tunnel extends Thread {

    public Socket socket, server;
    public InputStream clientIn, serverIn;
    public OutputStream clientOut, serverOut;

    // THERE WILL BE NO NMETHODS OR METHODS WITH SOCKS 5
    //   +----+----------+----------+
    //   |VER | NMETHODS | METHODS  |
    //   +----+----------+----------+
    //   | 1  |    1     | 1 to 255 |
    //   +----+----------+----------+

    public Tunnel(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run(){
        try{
            socket.setSoTimeout(1000);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);

            clientIn = socket.getInputStream();
            clientOut = socket.getOutputStream();

            Commons commons = null;
            byte socksVersion = getByte();

        //SOCKS5 PROXY
            if(socksVersion == 0x05){
                socket.setSoTimeout(15);
                socket.setKeepAlive(false);

                commons = new Socks5(this);

        //SOCKS4 PROXY
            }else if(socksVersion == 0x04){
                socket.setSoTimeout(15);
                socket.setKeepAlive(false);

                commons = new Socks4(this);

        //HTTP|HTTPS PROXY
            }else if(socksVersion == 67 || socksVersion == 71 || socksVersion == 80){
                socket.setSoTimeout(1000);
                socket.setKeepAlive(true);

                new HttpHttps(this);

                quickClose(socket);
                quickClose(server);
                return;

        //POSSIBLE PING, JUST KILL IT...
            }else{
                quickClose(socket);
                return;
            }

            byte command = commons.getCommand();
        //WORKING PERFECTLY - SOCKS 5
            if(command == 0x01){
                System.out.println("CONNECT");
                commons.connect();
                relay();

        //WORKING PERFECTLY - SOCKS 5
            }else if(command == 0x02){
                System.out.println("BIND");
                commons.bind();
                relay();

        //WORKING PERFECTLY
            }else if(command == 0x03){
                System.out.println("UDP");
                ((Socks5)commons).udp();
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            quickClose(socket);
            quickClose(server);
        }
    }


    //BETTER
    public void relay(){
        byte[] buffer = new byte[4096];
        int length;
        boolean active = true;
        while(active){
            //CLIENT TO SERVER
            try{
                length = clientIn.read(buffer);
            }catch(InterruptedIOException e){
                length = 0;
            }catch(IOException e){
                length = -1;
            }catch(Exception e){
                length = -1;
            }

            if(length < 0){
                active = false;
            }else if(length > 0){
                try{
                    serverOut.write(buffer, 0, length);
                    serverOut.flush();
                }catch(Exception e){
                }
            }


            //SERVER TO CLIENT
            try{
                length = serverIn.read(buffer);
            }catch(InterruptedIOException e){
                length = 0;
            }catch(IOException e){
                length = -1;
            }catch(Exception e){
                length = -1;
            }

            if(length < 0){
                active = false;
            }else if(length > 0){
                try{
                    clientOut.write(buffer, 0, length);
                    clientOut.flush();
                }catch(Exception e){
                }
            }
        }
    }

    public InetAddress resolveExternalAddress(){
        InetAddress	inetAddress = null;

        String[] hosts = {"www.sun.com","www.microsoft.com",
                "www.aol.com","www.altavista.com",
                "www.mirabilis.com","www.yahoo.com" };

        for(int i = 0; i < hosts.length; i++){
            try{
                Socket sct = new Socket(InetAddress.getByName(hosts[i]),80);
                inetAddress = sct.getLocalAddress();
                sct.close();
                break;
            }catch(Exception e){
            }
        }

        return inetAddress;
    }

    //BETTER
    public byte getByte(){
        int bit;
        while(!socket.isClosed()){
            try{
                bit = clientIn.read();
            }catch(Exception e){
                continue;
            }
            return (byte)bit;
        }
        return -1;
    }

    //BETTER
    public void sendToClient(byte[] buffer, int length){
        try{
            clientOut.write(buffer, 0, length);
            clientOut.flush();
        }catch(Exception e){
        }
    }

    public int byte2int(byte bit){
        int	res = bit;
        if(res < 0){
            res = (int)(0x100+res);
        }
        return res;
    }

    public static void quickClose(Socket socket){
        try{
            if(!socket.isOutputShutdown()){
                socket.shutdownOutput();
            }
            if(!socket.isInputShutdown()){
                socket.shutdownInput();
            }

            socket.close();
        }catch(Exception e){
            //e.printStackTrace();
        }
    }
}
