package unet.openproxy.Proxy;

import java.io.IOException;
import java.net.*;

public class Socks5 implements Commons {

    private Tunnel tunnel;
    private byte[] byteAddress = new byte[255];
    private InetSocketAddress address;
    private InetAddress udpAddress;
    private int udpPort;
    private DatagramPacket packet;

    //   +----+--------+
    //   |VER | METHOD |
    //   +----+--------+
    //   | 1  |   1    |
    //   +----+--------+

    //SEND AUTHENTICATION
    // 0x00 - NO AUTHENTICATION REQUIRED

    //   +----+-----+-------+------+----------+----------+
    //   |VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
    //   +----+-----+-------+------+----------+----------+
    //   | 1  |  1  | X'00' |  1   | Variable |    2     |
    //   +----+-----+-------+------+----------+----------+

    //SEND
    // 0xFF - FALSE VERSION
    // 0x07 - INVALID VERSION
    // 0x08 - INVALID ATYPE

    //CONNECT PROTOCOL
    //BIND PROTOCOL
    //UDP PROTOCOL

    public Socks5(Tunnel tunnel){
        this.tunnel = tunnel;
    }

    @Override
    public byte getCommand(){
        authenticate();

        byte[] accept = { (byte)0x05, (byte)0x00 };
        tunnel.sendToClient(accept, accept.length);

        byte version = tunnel.getByte();
        byte command = tunnel.getByte();

        tunnel.getByte();
        byte atype = tunnel.getByte();

        int[] addressSize = { -1, 4, -1, -1, 16 };
        int addressLength = addressSize[atype];
        byteAddress[0] = tunnel.getByte();
        if(atype == 0x03){
            addressLength = byteAddress[0]+1;
        }

        for(int i = 1; i < addressLength; i++){
            byteAddress[i] = tunnel.getByte();
        }

        InetAddress inetAddress = calcInetAddress(atype, byteAddress);
        int port = ((tunnel.byte2int(tunnel.getByte()) << 8) | tunnel.byte2int(tunnel.getByte()));
        address = new InetSocketAddress(inetAddress, port);

        if(tunnel.proxy.containsRedirect(address)){
            address = tunnel.proxy.getRedirect(address);
        }

        if(version != 0x05){
            replyCommand((byte)0xFF);
        }

        if(command < 0x01 || command > 0x03){
            replyCommand((byte)0x07);
        }

        if(atype == 0x04){
            replyCommand((byte)0x08);
        }

        if(atype > 0x04 || atype <= 0){
            replyCommand((byte)0x08);
        }

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
            replyCommand((byte)04);
        }

        replyCommand((byte)00);
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

    public void udp()throws IOException {
        DatagramSocket udpSocket;

        byte[] buffer = new byte[4096];
        try{
            udpSocket = new DatagramSocket();
            packet = new DatagramPacket(buffer, buffer.length);
        }catch(IOException e){
            replyCommand((byte)0x05);
            return;
        }

        InetAddress myAddress = tunnel.socket.getLocalAddress();
        int myPort = udpSocket.getLocalPort();

        udpReply((byte)0, myAddress, myPort);

        myAddress = tunnel.socket.getInetAddress();
        myPort = tunnel.socket.getPort();

        while(tunnel.clientIn.available() >= 0){
            try{
                udpSocket.receive(packet);
            }catch(Exception e){
                continue;
            }

            if(myAddress.equals(packet.getAddress())){
                myPort = packet.getPort();

                byte[] buf = clearDgpHead(packet.getData());
                if(buf == null){
                    continue;
                }

                if(buf.length <= 0 || udpAddress == null || udpPort == 0){
                    continue;
                }

                if(address.getAddress() != udpAddress || address.getPort() != udpPort){
                    address = new InetSocketAddress(udpAddress, udpPort);
                }

                DatagramPacket send = new DatagramPacket(buf, buf.length, udpAddress, udpPort);

                try{
                    udpSocket.send(send);
                }catch(Exception e){
                }
            }else{
                InetAddress dgAddress = packet.getAddress();
                int dgPort = packet.getPort();

                byte[] buf = addDgpHead(buffer);
                if(buf == null){
                    continue;
                }

                DatagramPacket send = new DatagramPacket(buf, buf.length, myAddress, myPort);
                try{
                    udpSocket.send(send);
                }catch(Exception e){
                }

                if(dgAddress != udpAddress || dgPort != udpPort){
                    address = new InetSocketAddress(dgAddress, dgPort);
                }
            }

            try{
                packet = new DatagramPacket(buffer, buffer.length);
            }catch(Exception e){
                break;
            }
        }
        udpSocket.close();
    }

    private byte[] addDgpHead(byte[] buffer){
        byte byteAddress[] = packet.getAddress().getAddress();
        int dgPort = packet.getPort();
        int headerLength = 6+byteAddress.length;
        int dataLength = packet.getLength();
        int packetLength = headerLength+dataLength;

        byte ubuffer[] = new byte[packetLength];

        ubuffer[0] = (byte)0x00;
        ubuffer[1] = (byte)0x00;
        ubuffer[2] = (byte)0x00;
        ubuffer[3] = (byte)0x01;
        System.arraycopy(byteAddress,0, ubuffer,4, byteAddress.length);
        ubuffer[4+byteAddress.length] = (byte)((dgPort >> 8) & 0xFF);
        ubuffer[5+byteAddress.length] = (byte)((dgPort) & 0xFF);
        System.arraycopy(buffer,0, ubuffer, 6+byteAddress.length, dataLength);
        System.arraycopy(ubuffer,0, buffer,0, packetLength);

        return ubuffer;
    }

    private byte[] clearDgpHead(byte[] buffer){
        int addressLength;
        int p = 4;

        byte atype = buffer[3];

        switch(atype){
            case 0x01:
                addressLength = 4;
                break;
            case 0x03:
                addressLength = buffer[p]+1;
                break;
            default:
                return null;
        }

        byte[] byteAddress = new byte[addressLength];
        System.arraycopy(buffer, p, byteAddress, 0, addressLength);
        p += addressLength;

        udpAddress = calcInetAddress(atype, byteAddress);
        udpPort = ((tunnel.byte2int(buffer[p++]) << 8) | tunnel.byte2int(buffer[p++]));

        if(udpAddress == null){
            return null;
        }

        int	dataLength = packet.getLength();
        dataLength -= p;

        byte ubuffer[] = new byte[dataLength];
        System.arraycopy(buffer,p, ubuffer,0, dataLength);
        System.arraycopy(ubuffer,0, buffer,0, dataLength);

        return ubuffer;
    }

    private boolean authenticate(){
        byte method = tunnel.getByte();
        String methods = "";

        for(int i = 0; i < method; i++){
            methods += ",-"+tunnel.getByte()+'-';
        }

        return (methods.indexOf("-0-") != -1 || methods.indexOf("-00-") != -1);
    }

    private InetAddress calcInetAddress(byte atype, byte[] byteAddress){
        InetAddress inetAddress = null;
        String address = "";

        if(atype == 0x01){
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

        }else if(atype == 0x03){
            if(byteAddress[0] <= 0){
                return null;
            }

            for(int i = 1; i <= byteAddress[0]; i++){
                address += (char)byteAddress[i];
            }

            try{
                inetAddress = InetAddress.getByName(address);
            }catch(UnknownHostException e){
                return null;
            }
        }

        return inetAddress;
    }

    private void replyCommand(byte replyCode){
        byte[] reply = new byte[10];

        if(byteAddress == null){
            byteAddress = new byte[4];
            address = new InetSocketAddress(address.getAddress(), 0);
        }

        reply[0] = 0x05;
        reply[1] = replyCode;
        reply[2] = 0x00;
        reply[3] = 0x01;
        reply[4] = byteAddress[0];
        reply[5] = byteAddress[1];
        reply[6] = byteAddress[2];
        reply[7] = byteAddress[3];
        reply[8] = (byte)((address.getPort() & 0xFF00) >> 8);
        reply[9] = (byte)(address.getPort() & 0x00FF);

        tunnel.sendToClient(reply, reply.length);
    }

    private void bindReply(byte replyCode, InetAddress inetAddress, int port){
        byte ip[] = { 0, 0, 0, 0 };
        byte[] reply = new byte[10];

        if(inetAddress != null){
            ip = inetAddress.getAddress();
        }

        reply[0] = 0x05;
        reply[1] = replyCode;
        reply[2] = 0x00;
        reply[3] = 0x01;
        reply[4] = ip[0];
        reply[5] = ip[1];
        reply[6] = ip[2];
        reply[7] = ip[3];
        reply[8] = (byte)((port & 0xFF00) >> 8);
        reply[9] = (byte)(port & 0x00FF);

        tunnel.sendToClient(reply, reply.length);
    }

    private void udpReply(byte replyCode, InetAddress inetAddress, int port){
        byte ip[] = { 0, 0, 0, 0 };
        byte[] reply = new byte[10];

        if(inetAddress != null){
            ip = inetAddress.getAddress();
        }

        reply[0] = 0x05;
        reply[1] = replyCode;
        reply[2] = 0x00;
        reply[3] = 0x01;
        reply[4] = ip[0];
        reply[5] = ip[1];
        reply[6] = ip[2];
        reply[7] = ip[3];

        reply[8] = (byte)((port & 0xFF00) >> 8);
        reply[9] = (byte)(port & 0x00FF);

        tunnel.sendToClient(reply, reply.length);
    }
}
