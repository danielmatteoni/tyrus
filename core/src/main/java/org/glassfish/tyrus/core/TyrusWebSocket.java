/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.tyrus.core;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.CloseReason;
import javax.websocket.SendHandler;

import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.CloseFrame;
import org.glassfish.tyrus.core.frame.PingFrame;
import org.glassfish.tyrus.core.frame.PongFrame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.spi.UpgradeRequest;

/**
 * Tyrus representation of web socket connection.
 * <p/>
 * Instance of this class represents one bi-directional websocket connection.
 */
public class TyrusWebSocket {
    private final TyrusEndpoint tyrusEndpoint;
    private final ProtocolHandler protocolHandler;
    private final CountDownLatch onConnectLatch = new CountDownLatch(1);
    private final EnumSet<State> connected = EnumSet.range(State.CONNECTED, State.CLOSING);
    private final AtomicReference<State> state = new AtomicReference<State>(State.NEW);

    /**
     * Create new instance, set {@link ProtocolHandler} and register {@link TyrusEndpoint}.
     *
     * @param protocolHandler used for writing data (sending).
     * @param tyrusEndpoint   notifies registered endpoints about incoming events.
     */
    public TyrusWebSocket(final ProtocolHandler protocolHandler,
                          final TyrusEndpoint tyrusEndpoint) {
        this.protocolHandler = protocolHandler;
        this.tyrusEndpoint = tyrusEndpoint;
        protocolHandler.setWebSocket(this);
    }

    /**
     * Sets the timeout for the writing operation.
     *
     * @param timeoutMs timeout in milliseconds.
     */
    public void setWriteTimeout(long timeoutMs) {
        // do nothing.
    }

    /**
     * Convenience method to determine if this {@link TyrusWebSocket} is connected.
     *
     * @return {@code true} if the {@link TyrusWebSocket} is connected, otherwise
     * {@code false}
     */
    public boolean isConnected() {
        return connected.contains(state.get());
    }

    /**
     * This callback will be invoked when the remote end-point sent a closing
     * frame.
     *
     * @param frame the close frame from the remote end-point.
     */
    public void onClose(CloseFrame frame) {
        final CloseReason closeReason = frame.getCloseReason();

        if (tyrusEndpoint != null) {
            tyrusEndpoint.onClose(this, closeReason);
        }
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
        } else {
            state.set(State.CLOSED);
            protocolHandler.doClose();
        }
    }

    /**
     * This callback will be invoked when the opening handshake between both
     * endpoints has been completed.
     *
     * @param upgradeRequest request associated with this socket.
     */
    public void onConnect(UpgradeRequest upgradeRequest) {
        state.set(State.CONNECTED);

        if (tyrusEndpoint != null) {
            tyrusEndpoint.onConnect(this, upgradeRequest);
        }

        onConnectLatch.countDown();
    }

    /**
     * This callback will be invoked when a fragmented binary message has
     * been received.
     *
     * @param last  flag indicating whether or not the payload received is the
     *              final fragment of a message.
     * @param frame the binary data received from the remote end-point.
     */
    public void onFragment(boolean last, BinaryFrame frame) {
        awaitOnConnect();
        if (tyrusEndpoint != null) {
            tyrusEndpoint.onFragment(this, frame.getPayloadData(), last);
        }
    }

    /**
     * This callback will be invoked when a fragmented textual message has
     * been received.
     *
     * @param last  flag indicating whether or not the payload received is the
     *              final fragment of a message.
     * @param frame the text received from the remote end-point.
     */
    public void onFragment(boolean last, TextFrame frame) {
        awaitOnConnect();
        if (tyrusEndpoint != null) {
            tyrusEndpoint.onFragment(this, frame.getTextPayload(), last);
        }
    }

    /**
     * This callback will be invoked when a binary message has been received.
     *
     * @param frame the binary data received from the remote end-point.
     */
    public void onMessage(BinaryFrame frame) {
        awaitOnConnect();
        if (tyrusEndpoint != null) {
            tyrusEndpoint.onMessage(this, frame.getPayloadData());
        }
    }

    /**
     * This callback will be invoked when a text message has been received.
     *
     * @param frame the text received from the remote end-point.
     */
    public void onMessage(TextFrame frame) {
        awaitOnConnect();
        if (tyrusEndpoint != null) {
            tyrusEndpoint.onMessage(this, frame.getTextPayload());
        }
    }

    /**
     * This callback will be invoked when the remote end-point has sent a ping
     * frame.
     *
     * @param frame the ping frame from the remote end-point.
     */
    public void onPing(PingFrame frame) {
        awaitOnConnect();
        if (tyrusEndpoint != null) {
            tyrusEndpoint.onPing(this, frame.getPayloadData());
        }
    }

    /**
     * This callback will be invoked when the remote end-point has sent a pong
     * frame.
     *
     * @param frame the pong frame from the remote end-point.
     */
    public void onPong(PongFrame frame) {
        awaitOnConnect();
        if (tyrusEndpoint != null) {
            tyrusEndpoint.onPong(this, frame.getPayloadData());
        }
    }

    /**
     * Closes this {@link TyrusWebSocket}.
     */
    public void close() {
        close(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), null);
    }

    /**
     * Closes this {@link TyrusWebSocket} using the specified status code and
     * reason.
     *
     * @param code   the closing status code.
     * @param reason the reason, if any.
     */
    public void close(int code, String reason) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(code, reason);
        }
    }

    /**
     * Send a binary frame to the remote endpoint.
     *
     * @param data data to be sent.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> send(byte[] data) {
        if (isConnected()) {
            return protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    /**
     * Send a binary frame to the remote endpoint.
     *
     * @param data    data to be sent.
     * @param handler {@link SendHandler#onResult(javax.websocket.SendResult)} will be called when sending is complete.
     */
    public void send(byte[] data, SendHandler handler) {
        if (isConnected()) {
            protocolHandler.send(data, handler);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    /**
     * Send a text frame to the remote endpoint.
     *
     * @param data data to be sent.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> send(String data) {
        if (isConnected()) {
            return protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    /**
     * Send a text frame to the remote endpoint.
     *
     * @param data    data to be sent.
     * @param handler {@link SendHandler#onResult(javax.websocket.SendResult)} will be called when sending is complete.
     */
    public void send(String data, SendHandler handler) {
        if (isConnected()) {
            protocolHandler.send(data, handler);
        } else {
            throw new RuntimeException("Socket is not connected");
        }
    }

    /**
     * Send a frame to the remote endpoint.
     *
     * @param data complete data frame.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendRawFrame(ByteBuffer data) {
        if (isConnected()) {
            return protocolHandler.sendRawFrame(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    /**
     * Sends a <code>ping</code> frame with the specified payload (if any).
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendPing(byte[] data) {
        return send(new PingFrame(data));
    }

    /**
     * Sends a <code>ping</code> frame with the specified payload (if any).
     * <p/>
     * It may seem odd to send a pong frame, however, RFC-6455 states:
     * "A Pong frame MAY be sent unsolicited.  This serves as a
     * unidirectional heartbeat.  A response to an unsolicited Pong frame is
     * not expected."
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendPong(byte[] data) {
        return send(new PongFrame(data));
    }

    // return boolean, check return value
    private void awaitOnConnect() {
        try {
            onConnectLatch.await();
        } catch (InterruptedException e) {
            // do nothing.
        }
    }

    private Future<Frame> send(Frame frame) {
        if (isConnected()) {
            return protocolHandler.send(frame);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    /**
     * Sends a fragment of a complete message.
     *
     * @param last     boolean indicating if this message fragment is the last.
     * @param fragment the textual fragment to send.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> stream(boolean last, String fragment) {
        if (isConnected()) {
            return protocolHandler.stream(last, fragment);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    /**
     * Sends a fragment of a complete message.
     *
     * @param last  boolean indicating if this message fragment is the last.
     * @param bytes the binary fragment to send.
     * @param off   the offset within the fragment to send.
     * @param len   the number of bytes of the fragment to send.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> stream(boolean last, byte[] bytes, int off, int len) {
        if (isConnected()) {
            return protocolHandler.stream(last, bytes, off, len);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    enum State {
        NEW, CONNECTED, CLOSING, CLOSED
    }
}
