# OpenProxy
Java Socks4/Socks5 - HTTP/HTTPS proxy server all within one port.
OpenProxy is a lightweight proxy server that allows you to have an all in one proxy without requiring more than 1 port open.

# How it works
We take the first byte as we would with a regular socks proxy and check if the byte is C/G/P aka first byte for the HTTP/HTTPS protocol. We then route the socket through a simple HTTP/HTTPS proxy if it matches, if not it gets routed over SOCKS.

# Supports
Socks4 - Connect | Bind<br>
Socks5 - Connect | Bind | UDP<br>
HTTP   - Get | Post<br>
HTTPS  - Connect

