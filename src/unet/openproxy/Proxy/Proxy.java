package unet.openproxy.Proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Proxy {

    private int port;
    private Socket socket;

    private Map<InetSocketAddress, InetSocketAddress> redirects;

    public Proxy(int port){
        this.port = port;
        redirects = new HashMap<>();
    }

    public void start()throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Proxy started on port 8080");

        while((socket = serverSocket.accept()) != null){
            (new Tunnel(this, socket)).start();
        }
    }

    public void addRedirect(InetSocketAddress from, InetSocketAddress to){
        redirects.put(from, to);
    }

    public boolean containsRedirect(InetSocketAddress address){
        return redirects.containsKey(address);
    }

    public InetSocketAddress getRedirect(InetSocketAddress address){
        return redirects.get(address);
    }

    public void stop(){

    }
}
