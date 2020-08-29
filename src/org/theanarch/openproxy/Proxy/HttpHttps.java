package org.theanarch.openproxy.Proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpHttps {

    private Tunnel tunnel;

    public HttpHttps(Tunnel tunnel)throws Exception {
        this.tunnel = tunnel;

        Pattern pattern = Pattern.compile("(ET|ONNECT|OST) (.+)(://|:)(.+) HTTP/(1\\.[01])", Pattern.CASE_INSENSITIVE);
        String response = readLine();
        Matcher matcher = pattern.matcher(response.split("\r\n")[0]);

        if(matcher.matches()){
            if(matcher.group(1).equals("ONNECT")){
                System.err.println("CONNECT");
                connect(matcher);
            }else if(matcher.group(1).equals("ET") || matcher.group(1).equals("OST")){
                URL url = new URL(matcher.group(2)+matcher.group(3)+matcher.group(4));
                System.err.println("GET");
                get(url, response);
            }
        }
    }

    public void connect(Matcher matcher)throws Exception {
        InetAddress address = InetAddress.getByName(new URL("https://"+matcher.group(2)).getHost());
        tunnel.server = new Socket(address, Integer.parseInt(matcher.group(4)));

        tunnel.clientOut.write(("HTTP/"+matcher.group(5)+" 200 Connection established\r\n").getBytes());
        tunnel.clientOut.write(("Protocol-agent: UNet/0.1\r\n").getBytes());
        tunnel.clientOut.write(("\r\n").getBytes());
        tunnel.clientOut.flush();

        new Thread(new Runnable(){
            @Override
            public void run(){
                forwardData(tunnel.server, tunnel.socket);
            }
        }).start();

        forwardData(tunnel.socket, tunnel.server);
    }

    public void get(URL url, String response)throws Exception {
        InetAddress address = InetAddress.getByName(url.getHost());
        tunnel.server = new Socket(address, 80);

        byte[] buffer = response.getBytes();
        OutputStream out = tunnel.server.getOutputStream();
        out.write(buffer, 0, buffer.length);
        out.flush();

        new Thread(new Runnable(){
            @Override
            public void run(){
                forwardData(tunnel.socket, tunnel.server);
            }
        }).start();

        forwardData(tunnel.server, tunnel.socket);
    }

    public String readLine()throws IOException {
        //VERSION 2.5
        String builder = "";
        char i;
        while(tunnel.clientIn.available() > 0){
            int c = tunnel.clientIn.read();

            i = ((char)((byte)c));
            builder += i;
            if(builder.endsWith("\r\n\r\n")){
                break;
            }
        }

        return builder;
    }

    public static void forwardData(Socket inputSocket, Socket outputSocket){
        try{
            InputStream inputStream = inputSocket.getInputStream();
            try{
                OutputStream outputStream = outputSocket.getOutputStream();
                try{
                    byte[] buffer = new byte[4096];//4096
                    int read;
                    do{
                        read = inputStream.read(buffer);
                        if(read > 0){
                            outputStream.write(buffer, 0, read);
                            if(inputStream.available() < 1){
                                outputStream.flush();
                            }
                        }
                    }while(read >= 0);
                }catch(Exception e){
                }finally{
                    if(!outputSocket.isOutputShutdown()){
                        outputSocket.shutdownOutput();
                    }
                }
            }finally{
                if(!inputSocket.isInputShutdown()){
                    inputSocket.shutdownInput();
                }
            }
        }catch(IOException e){
        }
    }

}
