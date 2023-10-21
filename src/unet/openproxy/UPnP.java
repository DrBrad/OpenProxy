package unet.openproxy;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UPnP {

    private GateWay gateWay;
    /*
    HTTP/1.1 200 OK
    CACHE-CONTROL: max-age=1800
    DATE: Mon, 06 Apr 2020 11:40:49 GMT
    EXT:
    LOCATION: http://10.0.0.1:49152/IGDdevicedesc_brlan0.xml
    OPT: "http://schemas.upnp.org/upnp/1/0/"; ns=01
    01-NLS: e6ae61c0-6803-11ea-96ac-ec4c173a3226
    SERVER: Linux/3.12.14, UPnP/1.0, Portable SDK for UPnP devices/1.6.22
    X-User-Agent: redsonic
    ST: urn:schemas-upnp-org:service:WANIPConnection:1
    USN: uuid:ebf5a0a0-1dd1-11b2-a93f-f44b2af79a13::urn:schemas-upnp-org:service:WANIPConnection:1
    */

    public UPnP(){
        String[] requests = {
                "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n\r\n",
                "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nST: urn:schemas-upnp-org:service:WANIPConnection:1\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n\r\n",
                "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nST: urn:schemas-upnp-org:service:WANPPPConnection:1\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n\r\n"
        };
        //THIS REQUEST CAN BE USED TO FIND OTHER DEVICES...
        //"M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: ssdp:discover\r\nMX: 10\r\nST: ssdp:all\r\n\r\n".getBytes();

        for(Inet4Address address : getLocalIPs()){
            for(String request : requests){
                try{
                    byte[] buffer = request.getBytes();

                    DatagramSocket datagramSocket = new DatagramSocket(new InetSocketAddress(address, 0));
                    datagramSocket.setSoTimeout(3000);
                    datagramSocket.send(new DatagramPacket(buffer, buffer.length, new InetSocketAddress("239.255.255.250", 1900)));

                    while(true){
                        try{
                            DatagramPacket recv = new DatagramPacket(new byte[1536], 1536);
                            datagramSocket.receive(recv);
                            gateWay = new GateWay(recv.getData(), address);
                        }catch(SocketTimeoutException t){
                            break;
                        }catch(Exception e){
                        }
                    }

                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean openPortTCP(int port){
        return gateWay.openPort(port, false);
    }

    public boolean openPortUDP(int port){
        return gateWay.openPort(port, true);
    }

    public boolean closePortTCP(int port){
        return gateWay.closePort(port, false);
    }

    public boolean closePortUDP(int port){
        return gateWay.closePort(port, true);
    }

    public String getExternalIp(){
        return gateWay.getExternalIP();
    }

    public boolean isMappedTCP(int port){
        return gateWay.isMapped(port, false);
    }

    public boolean isMappedUDP(int port){
        return gateWay.isMapped(port, false);
    }

    public Inet4Address[] getLocalIPs(){
        LinkedList<Inet4Address> ret = new LinkedList<>();
        try{
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while(ifaces.hasMoreElements()){
                try{
                    NetworkInterface iface = ifaces.nextElement();
                    if(!iface.isUp() || iface.isLoopback() || iface.isVirtual() || iface.isPointToPoint()){
                        continue;
                    }
                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    if(addrs == null){
                        continue;
                    }
                    while(addrs.hasMoreElements()){
                        InetAddress addr = addrs.nextElement();
                        if(addr instanceof Inet4Address){
                            ret.add((Inet4Address) addr);
                        }
                    }
                }catch(Exception e){
                }
            }
        }catch(Exception e){
        }
        return ret.toArray(new Inet4Address[]{});
    }

    class GateWay {

        private Inet4Address address;
        private String controlUrl, serviceType;

        public GateWay(byte[] data, Inet4Address address)throws Exception {
            this.address = address;

            String response = new String(data).trim();
            Pattern pattern = Pattern.compile("Location:(?: |)(.*?)$", Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

            Matcher matcher = pattern.matcher(response);
            while(matcher.find()){
                controlUrl = matcher.group(1);
            }

            pattern = Pattern.compile("(<controlURL>|<serviceType>)(.*?)(?:<\\/controlURL>|<\\/serviceType>)", Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
            HttpURLConnection conn = (HttpURLConnection) new URL(controlUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "text/xml");

            byte[] buffer = new byte[8192];
            int length = conn.getInputStream().read(buffer);
            response = new String(buffer, 0, length);
            conn.disconnect();

            matcher = pattern.matcher(response);
            String urlPath = "";
            while(matcher.find()){
                if(matcher.group(1).equals("<serviceType>")){
                    serviceType = matcher.group(2);
                }else{
                    urlPath = matcher.group(2);
                }
            }

            try{
                URL url = new URL(controlUrl);
                controlUrl = url.getProtocol()+"://"+url.getHost()+":"+url.getPort()+urlPath;
            }catch(Exception e){
                throw new Exception("Couldn't parse url.");
            }
        }

        private HashMap<String, String> command(String action, Map<String, String> params)throws Exception {
            HashMap<String, String> ret = new HashMap<>();
            String soap = "<?xml version=\"1.0\"?>\r\n" +
                    "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<SOAP-ENV:Body>" +
                    "<m:"+action+" xmlns:m=\""+serviceType+"\">";

            if(params != null){
                for(Map.Entry<String, String> entry : params.entrySet()){
                    soap += "<"+entry.getKey()+">"+entry.getValue()+"</" + entry.getKey()+">";
                }
            }
            soap += "</m:"+action+"></SOAP-ENV:Body></SOAP-ENV:Envelope>";
            byte[] req = soap.getBytes();
            HttpURLConnection conn = (HttpURLConnection) new URL(controlUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/xml");
            conn.setRequestProperty("SOAPAction", "\""+serviceType+"#"+action+"\"");
            conn.setRequestProperty("Connection", "Close");
            conn.setRequestProperty("Content-Length", ""+req.length);
            conn.getOutputStream().write(req);

            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(conn.getInputStream());
            NodeIterator iterator = ((DocumentTraversal) document).createNodeIterator(document.getDocumentElement(), NodeFilter.SHOW_ELEMENT, null, true);
            Node node;
            while((node = iterator.nextNode()) != null){
                try{
                    if(node.getFirstChild().getNodeType() == Node.TEXT_NODE){
                        ret.put(node.getNodeName(), node.getTextContent());
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            conn.disconnect();
            return ret;
        }

        public boolean openPort(int port, boolean udp){
            if(port < 0 || port > 65535){
                throw new IllegalArgumentException("Invalid port");
            }
            HashMap<String, String> params = new HashMap<>();
            params.put("NewRemoteHost", "");
            params.put("NewProtocol", udp ? "UDP" : "TCP");
            params.put("NewInternalClient", address.getHostAddress());
            params.put("NewExternalPort", port+"");
            params.put("NewInternalPort", port+"");
            params.put("NewEnabled", "1");
            params.put("NewPortMappingDescription", "UNet");
            params.put("NewLeaseDuration", "0");
            try{
                HashMap<String, String> ret = command("AddPortMapping", params);
                return ret.get("errorCode") == null;
            }catch(Exception e){
                return false;
            }
        }

        public boolean closePort(int port, boolean udp){
            if(port < 0 || port > 65535){
                throw new IllegalArgumentException("Invalid port");
            }
            HashMap<String, String> params = new HashMap<>();
            params.put("NewRemoteHost", "");
            params.put("NewProtocol", udp ? "UDP" : "TCP");
            params.put("NewExternalPort", ""+port);
            try{
                command("DeletePortMapping", params);
                return true;
            }catch(Exception e){
                return false;
            }
        }

        public boolean isMapped(int port, boolean udp){
            if(port < 0 || port > 65535){
                throw new IllegalArgumentException("Invalid port");
            }
            HashMap<String, String> params = new HashMap<>();
            params.put("NewRemoteHost", "");
            params.put("NewProtocol", udp ? "UDP" : "TCP");
            params.put("NewExternalPort", ""+port);
            try{
                HashMap<String, String> ret = command("GetSpecificPortMappingEntry", params);
                if(ret.get("errorCode") != null){
                    throw new Exception();
                }
                return ret.get("NewInternalPort") != null;
            }catch(Exception e){
                return false;
            }
        }

        public String getExternalIP(){
            try{
                HashMap<String, String> ret = command("GetExternalIPAddress", null);
                return ret.get("NewExternalIPAddress");
            }catch(Exception e){
                return null;
            }
        }
    }
}
