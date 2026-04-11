package com.socket.edge.core;

import com.socket.edge.utils.JsonUtil;
import com.socket.edge.utils.PciMaskUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Structured audit trail writer for ISO 8583 message routing decisions.
 *
 * <p>Writes JSON-lines format ({@code .jsonl}) for easy ingestion by
 * ELK, Splunk, Datadog, or other log aggregation systems.</p>
 *
 * <p>Features:
 * <ul>
 *   <li>Async write via bounded queue — does not block message processing</li>
 *   <li>PCI field masking applied before writing</li>
 *   <li>Configurable field selection</li>
 *   <li>Auto-creates log directory on startup</li>
 * </ul>
 *
 * <p>Thread-safe. Uses a single writer thread to prevent file contention.</p>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private final boolean enabled;
    private final Set<String> fields;
    private final PciMaskUtil pciMaskUtil;
    private final BlockingQueue<Map<String, Object>> queue;
    private volatile boolean running = false;
    private Thread writerThread;
    private BufferedWriter writer;

    /**
     * Creates a new AuditLogger.
     *
     * @param config     audit configuration
     * @param baseDir    application base directory
     * @param pciMaskUtil PCI masking utility
     */
    public AuditLogger(SystemConfig.AuditConfig config, String baseDir, PciMaskUtil pciMaskUtil) {
        this.enabled = config.enabled();
        this.fields = Set.copyOf(config.fields());
        this.pciMaskUtil = pciMaskUtil;
        this.queue = new LinkedBlockingQueue<>(10_000);

        if (enabled) {
            try {
                Path logPath = Path.of(baseDir, config.path());
                Files.createDirectories(logPath.getParent());
                writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                startWriterThread();
                log.info("Audit logger started: path={}", logPath);
            } catch (IOException e) {
                log.error("Failed to initialize audit logger", e);
                throw new IllegalStateException("Failed to initialize audit logger", e);
            }
        }
    }

    /**
     * Creates a disabled (no-op) audit logger.
     */
    public static AuditLogger disabled() {
        return new AuditLogger();
    }

    private AuditLogger() {
        this.enabled = false;
        this.fields = Set.of();
        this.pciMaskUtil = PciMaskUtil.disabled();
        this.queue = null;
    }

    /**
     * Logs an audit entry for a processed message.
     *
     * @param ctx message context
     */
    public void log(MessageContext ctx) {
        if (!enabled || ctx == null) return;

        Map<String, Object> entry = new LinkedHashMap<>();

        for (String field : fields) {
            switch (field) {
                case "mti" -> entry.put("mti", ctx.field("de1"));
                case "correlation-key" -> entry.put("correlationKey", ctx.getCorrelationKey());
                case "channel" -> entry.put("channel", ctx.getChannelName());
                case "direction" -> entry.put("direction",
                        ctx.getDirection() != null ? ctx.getDirection().name() : null);
                case "timestamp" -> entry.put("timestamp", Instant.now().toString());
                case "latency-ns" -> {
                    Object receivedTime = ctx.getProperty("receivedTimeNs");
                    if (receivedTime instanceof Long startNs) {
                        entry.put("latencyNs", System.nanoTime() - startNs);
                    }
                }
                case "source-ip" -> {
                    if (ctx.getRemoteAddress() != null) {
                        entry.put("sourceIp", ctx.getRemoteAddress().getHostString()
                                + ":" + ctx.getRemoteAddress().getPort());
                    }
                }
                case "dest-ip" -> {
                    if (ctx.getLocalAddress() != null) {
                        entry.put("destIp", ctx.getLocalAddress().getHostString()
                                + ":" + ctx.getLocalAddress().getPort());
                    }
                }
                default -> {
                    // Check if it's an ISO field reference (e.g. "de2", "de11")
                    if (field.startsWith("de")) {
                        String value = ctx.field(field);
                        if (value != null) {
                            entry.put(field, pciMaskUtil.mask(field, value));
                        }
                    }
                }
            }
        }

        // Non-blocking enqueue — drop if queue is full
        if (!queue.offer(entry)) {
            log.warn("Audit queue full, dropping entry: corrKey={}",
                    ctx.getCorrelationKey());
        }
    }

    private void startWriterThread() {
        running = true;
        writerThread = new Thread(() -> {
            while (running || !queue.isEmpty()) {
                try {
                    Map<String, Object> entry = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (entry != null) {
                        writer.write(JsonUtil.toJson(entry));
                        writer.newLine();
                        writer.flush();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    log.error("Audit write failed", e);
                }
            }
        }, "audit-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    /**
     * Shuts down the audit logger, flushing remaining entries.
     */
    public void shutdown() {
        if (!enabled) return;

        running = false;
        if (writerThread != null) {
            try {
                writerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                log.error("Failed to close audit writer", e);
            }
        }
        log.info("Audit logger shutdown");
    }
}
