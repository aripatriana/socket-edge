package com.socket.edge.simulator;

public class SimpleNettyClientNode2 extends SimpleNettyClient{

    public SimpleNettyClientNode2() {
        super("node2");
    }

    public static void main(String[] args) throws Exception {
        new SimpleNettyClientNode2().start("127.0.0.1", 27000);
    }

}
