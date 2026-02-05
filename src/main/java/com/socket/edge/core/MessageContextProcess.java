package com.socket.edge.core;

import org.apache.camel.ProducerTemplate;

/**
 * Component responsible for dispatching {@link MessageContext}
 * into the Apache Camel routing layer.
 *
 * <p>This class acts as a boundary between the application logic
 * and Camel routes, delegating message delivery via
 * {@link ProducerTemplate}.</p>
 *
 * <p>The message is sent asynchronously to the
 * {@code seda:receive} endpoint, allowing non-blocking processing
 * and decoupling message producers from consumers.</p>
 *
 * <p>Typical use cases:
 * <ul>
 *   <li>Forwarding ISO message context to Camel routes</li>
 *   <li>Asynchronous message handling via SEDA</li>
 *   <li>Decoupling transport layer from processing pipeline</li>
 * </ul>
 *
 * <p>Thread safety:
 * <ul>
 *   <li>{@link ProducerTemplate} is thread-safe</li>
 *   <li>This class is safe to be used concurrently</li>
 * </ul>
 *
 *  @author Ari Patriana
 *  @since 1.0.0
 */
public class MessageContextProcess {

    /**
     * Apache Camel producer used to send messages to Camel endpoints.
     */
    private final ProducerTemplate producerTemplate;

    /**
     * Creates a new {@code MessageContextProcess}.
     *
     * @param producerTemplate Camel {@link ProducerTemplate} used
     *                         to dispatch messages
     */
    public MessageContextProcess(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    /**
     * Sends the given {@link MessageContext} to the Camel route.
     *
     * <p>The message is delivered to the {@code seda:receive} endpoint
     * for asynchronous processing.</p>
     *
     * @param messageContext message context to be processed
     */
    public void process(MessageContext messageContext) {
        producerTemplate.sendBody("seda:receive", messageContext);
    }
}
