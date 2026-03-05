package com.socket.edge.core;

/**
 * A callback interface used to notify the completion status of an operation.
 *
 * <p>This callback provides two outcome methods:
 * <ul>
 *   <li>{@link #onComplete(Object)} – invoked when the operation completes successfully</li>
 *   <li>{@link #onFailure(Object)} – invoked when the operation fails</li>
 * </ul>
 *
 * <p>The generic type {@code T} represents the context or result associated
 * with the operation (e.g. a socket, client instance, or request object).</p>
 *
 * @param <T> the type of the result or context passed to the callback
 *
 * @author ari.patriana
 */
public interface CompletionCallback<T> {

    /**
     * Called when the operation completes successfully.
     *
     * @param t the result or context of the completed operation
     */
    void onComplete(T t);

    /**
     * Called when the operation fails.
     *
     * @param t the result or context associated with the failed operation
     */
    void onFailure(T t);
}