package com.socket.edge.core;

import org.apache.camel.ProducerTemplate;

public class MessageContextProcess {

    ProducerTemplate producerTemplate;

    public MessageContextProcess(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    public void process(MessageContext messageContext) {
        producerTemplate.sendBody("seda:receive", messageContext);
    }
}
