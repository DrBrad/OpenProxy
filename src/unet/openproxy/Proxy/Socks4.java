package unet.openproxy.Proxy;

import java.net.*;

public class Socks4 implements Commons {

    private Tunnel tunnel;
    private byte[] byteAddress = new byte[4], bytePort = new byte[2];
    private InetSocketAddress address;

    public Socks4(Tunnel tunnel){
        this.tunnel = tunnel;
    }

    @Override
    public byte getCommand(){
        byte command = tunnel.getByte();
        bytePort[0] = tunnel.getByte();
        bytePort[1] = tunnel.getByte();

        for(int i = 0; i < 4; i++){
            byteAddress[i] = tunnel.getByte();
        }

        while(tunnel.getByte() != 0x00){
        }

        if(command < 0x01 || command > 0x02){
            replyCommand((byte)91);
        }

        InetAddress inetAddress = calcInetAddress();
        int port = ((tunnel.byte2int(bytePort[0]) << 8) | tunnel.byte2int(bytePort[1]));
        address = new InetSocketAddress(inetAddress, port);

        if(tunnel.proxy.containsRedirect(address)){
            address = tunnel.proxy.getRedirect(address);
        }

        replyCommand((byte)90);

        return command;
    }

    @Override
    public void connect(){
        try{
            tunnel.server = new Socket(address.getAddress(), address.getPort());
            tunnel.server.setSoTimeout(10);
            tunnel.serverIn = tunnel.server.getInputStream();
            tunnel.serverOut = tunnel.server.getOutputStream();
        }catch(Exception e){
            replyCommand((byte)91);
        }

        replyCommand((byte)90);
    }

    @Override
    public void bind()throws Exception {
        InetAddress serverAddress = tunnel.resolveExternalAddress();
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
            tunnel.server = socket;
            tunnel.serverIn = socket.getInputStream();
            tunnel.serverOut = socket.getOutputStream();
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
            address += tunnel.byte2int(byteAddress[i]);
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

        tunnel.sendToClient(reply, reply.length);
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

        tunnel.sendToClient(reply, reply.length);
    }
}
