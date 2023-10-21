package unet.openproxy;

import unet.openproxy.Proxy.Proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ProxyTest {

    public static void main(String args[])throws IOException {
        Proxy proxy = new Proxy(8080);

        InetSocketAddress from = new InetSocketAddress("name.com", 443);
        InetSocketAddress to = new InetSocketAddress("name.com", 443);

        System.out.println(from.getAddress().getHostAddress()+" : "+from.getPort());
        proxy.addRedirect(from, to);

        from = new InetSocketAddress("name.com", 80);
        to = new InetSocketAddress("name.com", 80);

        System.out.println(from.getAddress().getHostAddress()+" : "+from.getPort());
        proxy.addRedirect(from, to);

        proxy.start();
    }
}
