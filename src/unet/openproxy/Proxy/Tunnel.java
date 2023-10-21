package unet.openproxy.Proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class Tunnel extends Thread {

    public Proxy proxy;
    public Socket socket, server;
    public InputStream clientIn, serverIn;
    public OutputStream clientOut, serverOut;

    // THERE WILL BE NO NMETHODS OR METHODS WITH SOCKS 5
    //   +----+----------+----------+
    //   |VER | NMETHODS | METHODS  |
    //   +----+----------+----------+
    //   | 1  |    1     | 1 to 255 |
    //   +----+----------+----------+

    public Tunnel(Proxy proxy, Socket socket){
        this.proxy = proxy;
        this.socket = socket;
    }

    @Override
    public void run(){
        try{
            socket.setSoTimeout(5000);
            socket.setKeepAlive(false);

            clientIn = socket.getInputStream();
            clientOut = socket.getOutputStream();

            Commons commons;

            switch(getByte()){
                case 0x05:
                    commons = new Socks5(this);
                    break;

                case 0x04:
                    commons = new Socks4(this);
                    break;

                case 67:
                case 71:
                case 80:
                    new HttpHttps(this);
                    relay();

                    quickClose(socket);
                    quickClose(server);
                    return;

                default:
                    quickClose(socket);
                    return;
            }

            switch(commons.getCommand()){
                case 0x01:
                    System.out.println("CONNECT");
                    commons.connect();
                    relay();
                    break;

                case 0x02:
                    System.out.println("BIND");
                    commons.bind();
                    relay();
                    break;

                case 0x03:
                    System.out.println("UDP");
                    ((Socks5)commons).udp();
                    break;
            }

        }catch(Exception e){
            e.printStackTrace();
        }finally{
            quickClose(socket);
            quickClose(server);
        }
    }


    public void relay(){
        Thread thread = new Thread(new Runnable(){
            @Override
            public void run(){
                try{
                    while(!socket.isClosed() && !server.isClosed() && !socket.isInputShutdown() && !server.isOutputShutdown() && !isInterrupted()){
                        byte[] buffer = new byte[4096];
                        int length;

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
                            socket.shutdownInput();
                            server.shutdownOutput();
                            break;
                        }else if(length > 0){
                            try{
                                serverOut.write(buffer, 0, length);
                                serverOut.flush();
                            }catch(Exception e){
                            }
                        }
                    }
                }catch(Exception e){
                }
            }
        });

        thread.start();

        try{
            byte[] buffer = new byte[4096];
            int length;

            while(!socket.isClosed() && !server.isClosed() && !server.isInputShutdown() && !socket.isOutputShutdown() && !thread.isInterrupted()){
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
                    server.shutdownInput();
                    socket.shutdownOutput();
                    break;
                }else if(length > 0){
                    try{
                        clientOut.write(buffer, 0, length);
                        clientOut.flush();
                    }catch(Exception e){
                    }
                }
            }

            thread.interrupt();
        }catch(Exception e){
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
