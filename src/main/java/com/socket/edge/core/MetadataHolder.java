package com.socket.edge.core;

import com.socket.edge.model.Metadata;

/**
 * Thread-safe holder for {@link Metadata} that supports atomic replacement.
 *
 * <p>This class uses a {@code volatile} reference to guarantee visibility of
 * updates across threads without requiring explicit synchronization.</p>
 *
 * <p>Typical use cases:
 * <ul>
 *   <li>Dynamic configuration reload</li>
 *   <li>Hot-swapping metadata at runtime</li>
 *   <li>Read-mostly access pattern with occasional updates</li>
 * </ul>
 *
 * <p>Note:
 * <ul>
 *   <li>Replacement is atomic (reference swap)</li>
 *   <li>The {@link Metadata} object itself should be immutable
 *       or externally synchronized if it is mutable</li>
 * </ul>
 *
 *  @author Ari Patriana
 *  @since 1.0.0
 */
public class MetadataHolder {

    /**
     * Volatile reference to ensure visibility of updates across threads.
     */
    private volatile Metadata metadata;

    /**
     * Creates a new holder with the initial metadata.
     *
     * @param metadata initial metadata instance, must not be {@code null}
     */
    public MetadataHolder(Metadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Replaces the current metadata with a new instance.
     *
     * <p>The replacement is immediately visible to all threads
     * calling {@link #get()}.</p>
     *
     * @param newMetadata new metadata instance, must not be {@code null}
     */
    public void replaceWith(Metadata newMetadata) {
        this.metadata = newMetadata;
    }

    /**
     * Returns the current metadata instance.
     *
     * @return current {@link Metadata}
     */
    public Metadata get() {
        return metadata;
    }
}
