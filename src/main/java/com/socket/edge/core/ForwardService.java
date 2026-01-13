package com.socket.edge.core;

import org.apache.camel.ProducerTemplate;

public class ForwardService {

    ProducerTemplate producerTemplate;

    public ForwardService(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    public void forward(MessageContext messageContext) {
        producerTemplate.sendBody("seda:receive", messageContext);
    }
}
