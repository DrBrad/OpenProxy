OpenProxy
===========

This is a proxy server and doesn't contain any client.
All of the proxies run on the same port, the default port is: ```8080```

Supports
-----------
This repo currently supports the following and I plan to implement the rest later on.

- [x] Socks4
- [x] Socks5
- [x] Http
- [x] Https
- [ ] FTP
- [ ] Shadow Socks
- [ ] SMTP
- [ ] POP
- [ ] DNS
- [ ] SSH
- [ ] IRC
- [x] UPnP support


How to run
-----------
Example:
```
java -jar OpenProxy.jar
```

How it works
-----------
We take the first byte as we would with a regular socks proxy and check if the byte is C/G/P aka first byte for the HTTP/HTTPS protocol. We then route the socket through a simple HTTP/HTTPS proxy if it matches, if not it gets routed over SOCKS.
