package unet.openproxy.Proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
        InetSocketAddress address = new InetSocketAddress(new URL("https://"+matcher.group(2)).getHost(), Integer.parseInt(matcher.group(4)));

        if(tunnel.proxy.containsRedirect(address)){
            address = tunnel.proxy.getRedirect(address);
        }

        tunnel.server = new Socket(address.getAddress(), address.getPort());
        tunnel.serverIn = tunnel.server.getInputStream();
        tunnel.serverOut = tunnel.server.getOutputStream();

        tunnel.clientOut.write(("HTTP/"+matcher.group(5)+" 200 Connection established\r\n").getBytes());
        tunnel.clientOut.write(("Protocol-agent: OpenProxy/0.1\r\n").getBytes());
        tunnel.clientOut.write(("\r\n").getBytes());
        tunnel.clientOut.flush();
    }

    public void get(URL url, String response)throws Exception {
        InetSocketAddress address = new InetSocketAddress(url.getHost(), 80);

        if(tunnel.proxy.containsRedirect(address)){
            address = tunnel.proxy.getRedirect(address);
        }

        tunnel.server = new Socket(address.getAddress(), address.getPort());
        tunnel.serverIn = tunnel.server.getInputStream();
        tunnel.serverOut = tunnel.server.getOutputStream();

        byte[] buffer = response.getBytes();
        tunnel.serverOut.write(buffer, 0, buffer.length);
        tunnel.serverOut.flush();
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
}
