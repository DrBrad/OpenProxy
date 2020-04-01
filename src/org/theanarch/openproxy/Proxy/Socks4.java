package org.theanarch.openproxy.Proxy;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class Socks4 implements Commons {

    private Thread thread;
    private byte[] byteAddress = new byte[4], bytePort = new byte[2];
    private InetAddress address;
    private int port;

    public Socks4(Thread thread){
        this.thread = thread;
    }

    @Override
    public byte getCommand(){
        byte command = thread.getByte();
        bytePort[0] = thread.getByte();
        bytePort[1] = thread.getByte();

        for(int i = 0; i < 4; i++){
            byteAddress[i] = thread.getByte();
        }

        while(thread.getByte() != 0x00){
        }

        if(command < 0x01 || command > 0x02){
            replyCommand((byte)91);
        }

        address = calcInetAddress();
        port = ((thread.byte2int(bytePort[0]) << 8) | thread.byte2int(bytePort[1]));

        replyCommand((byte)90);

        return command;
    }

    @Override
    public void connect(){
        try{
            thread.server = new Socket(address, port);
            thread.server.setSoTimeout(10);
            thread.serverIn = thread.server.getInputStream();
            thread.serverOut = thread.server.getOutputStream();
        }catch(Exception e){
            replyCommand((byte)91);
        }

        replyCommand((byte)90);
    }

    @Override
    public void bind()throws Exception {
        InetAddress serverAddress = thread.resolveExternalAddress();
        int serverPort = 0;

        ServerSocket serverSocket;
        try{
            serverSocket = new ServerSocket(0);
            serverPort = serverSocket.getLocalPort();
        }catch(Exception e){
            e.printStackTrace();
            bindReply((byte)2, serverAddress, serverPort);
            return;
        }

        bindReply((byte)0, serverAddress, serverPort);

        Socket socket;
        while((socket = serverSocket.accept()) != null){
            thread.server = socket;
            thread.serverIn = socket.getInputStream();
            thread.serverOut = socket.getOutputStream();
            break;
        }

        bindReply((byte)0, socket.getInetAddress(), socket.getPort());

        serverSocket.close();
    }

    public InetAddress calcInetAddress(){
        InetAddress	inetAddress;
        String address = "";

        if(byteAddress.length < 4){
            return null;
        }

        for(int i = 0; i < 4; i++){
            address += thread.byte2int(byteAddress[i]);
            if(i < 3){
                address += ".";
            }
        }

        try{
            inetAddress = InetAddress.getByName(address);
        }catch(UnknownHostException e){
            return null;
        }

        return inetAddress;
    }

    public void replyCommand(byte replyCode){
        byte[] reply = new byte[8];
        reply[0] = 0;
        reply[1] = replyCode;
        reply[2] = bytePort[0];
        reply[3] = bytePort[1];
        reply[4] = byteAddress[0];
        reply[5] = byteAddress[1];
        reply[6] = byteAddress[2];
        reply[7] = byteAddress[3];

        thread.sendToClient(reply, reply.length);
    }

    private void bindReply(byte replyCode, InetAddress inetAddress, int port){
        byte ip[] = { 0, 0, 0, 0 };

        byte[] reply = new byte[8];
        if(inetAddress != null){
            ip = inetAddress.getAddress();
        }

        reply[0] = 0;
        reply[1] = replyCode;
        reply[2] = (byte)((port & 0xFF00) >> 8);
        reply[3] = (byte) (port & 0x00FF);
        reply[4] = ip[0];
        reply[5] = ip[1];
        reply[6] = ip[2];
        reply[7] = ip[3];

        thread.sendToClient(reply, reply.length);
    }
}
