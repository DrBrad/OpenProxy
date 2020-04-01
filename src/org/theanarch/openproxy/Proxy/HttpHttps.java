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

    private Thread thread;

    public HttpHttps(Thread thread)throws Exception {
        this.thread = thread;

        Pattern pattern = Pattern.compile("(ET|ONNECT|OST) (.+)(://|:)(.+) HTTP/(1\\.[01])", Pattern.CASE_INSENSITIVE);

        for(int i = 0; i < 3000; i++){
            String response = readLine();
            Matcher matcher = pattern.matcher(response.split("\r\n")[0]);

            if(!response.equals("")){
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
                break;
            }
        }
    }

    public void connect(Matcher matcher)throws Exception {
        InetAddress address = InetAddress.getByName(new URL("https://"+matcher.group(2)).getHost());
        thread.server = new Socket(address, Integer.parseInt(matcher.group(4)));

        thread.clientOut.write(("HTTP/"+matcher.group(5)+" 200 Connection established\r\n").getBytes());
        thread.clientOut.write(("Protocol-agent: UNet/0.1\r\n").getBytes());
        thread.clientOut.write(("\r\n").getBytes());
        thread.clientOut.flush();

        new java.lang.Thread(new Runnable(){
            @Override
            public void run(){
                forwardData(thread.server, thread.socket);
            }
        }).start();

        forwardData(thread.socket, thread.server);
    }

    public void get(URL url, String response)throws Exception {
        InetAddress address = InetAddress.getByName(url.getHost());
        thread.server = new Socket(address, 80);

        byte[] buffer = response.getBytes();
        OutputStream out = thread.server.getOutputStream();
        out.write(buffer, 0, buffer.length);
        out.flush();

        new java.lang.Thread(new Runnable(){
            @Override
            public void run(){
                forwardData(thread.socket, thread.server);
            }
        }).start();

        forwardData(thread.server, thread.socket);
    }

    public String readLine()throws IOException {
        //VERSION 2.5
        String builder = "";
        char i;
        while(thread.clientIn.available() > 0){
            int c = thread.clientIn.read();

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
