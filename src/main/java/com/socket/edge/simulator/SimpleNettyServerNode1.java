package com.socket.edge.simulator;

public class SimpleNettyServerNode1 extends SimpleNettyServer {

    public SimpleNettyServerNode1() {
        super("node1");
    }

    public static void main(String[] args) throws Exception {
        new SimpleNettyServerNode1().start(26000);
    }

}
