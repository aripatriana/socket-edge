package com.socket.edge.simulator;

public class SimpleNettyServerNode2 extends SimpleNettyServer {

    public SimpleNettyServerNode2() {
        super("node2");
    }

    public static void main(String[] args) throws Exception {
        new SimpleNettyServerNode2().start(21000);
    }

}
