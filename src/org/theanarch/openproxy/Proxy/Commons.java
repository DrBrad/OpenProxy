package org.theanarch.openproxy.Proxy;

public interface Commons {

    byte getCommand();
    void connect();
    void bind()throws Exception;
}
