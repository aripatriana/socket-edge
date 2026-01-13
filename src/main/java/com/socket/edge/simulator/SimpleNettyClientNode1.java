package com.socket.edge.simulator;

public class SimpleNettyClientNode1 extends SimpleNettyClient{

    public SimpleNettyClientNode1() {
        super("node2");
    }

    public static void main(String[] args) throws Exception {
        new SimpleNettyClientNode1().start("127.0.0.1", 27000);
    }

}
