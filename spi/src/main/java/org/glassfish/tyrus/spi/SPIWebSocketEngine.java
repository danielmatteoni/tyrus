/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.spi;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Web Socket engine is the main entry-point to WebSocket implementation.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public interface SPIWebSocketEngine {

    /**
     * Handles upgrade process, response is written using {@link SPIWriter#write(SPIHandshakeResponse)}.
     *
     * @param writer  used to write HTTP response.
     * @param request representation of HTTP request.
     * @return {@code true} if upgrade was successful, {@code false} otherwise.
     */
    boolean upgrade(SPIWriter writer, SPIHandshakeRequest request);

    /**
     * Handles upgrade process, response is written using {@link SPIWriter#write(SPIHandshakeResponse)}.
     *
     * @param writer          used to write HTTP response.
     * @param request         representation of HTTP request.
     * @param upgradeListener {@link org.glassfish.tyrus.spi.SPIWebSocketEngine.UpgradeListener#onUpgradeFinished()}
     *                        is invoked after handshake response is sent. Registering this listener transfer
     *                        responsibility for calling {@link #onConnect(SPIWriter)} to this listener. This might be
     *                        useful especially when you need to wait for some other initialization (like Servlet update
     *                        mechanism); invoking {@link #onConnect(SPIWriter)} means that {@link javax.websocket.OnOpen}
     *                        annotated method will be invoked which allows sending messages, so underlying connection
     *                        needs to be ready.
     * @return {@code true} if upgrade was successful, {@code false} otherwise.
     */
    boolean upgrade(SPIWriter writer, SPIHandshakeRequest request, UpgradeListener upgradeListener);

    /**
     * Processes incoming data, including sending a response (if any).
     *
     * @param writer TODO
     * @param data   incoming data.
     */
    void processData(SPIWriter writer, ByteBuffer data);

    /**
     * Causes invocation if {@link javax.websocket.OnOpen} annotated method. Can be invoked only when
     * {@link #upgrade(SPIWriter, SPIHandshakeRequest, org.glassfish.tyrus.spi.SPIWebSocketEngine.UpgradeListener)} is used.
     *
     * @param writer TODO
     */
    void onConnect(SPIWriter writer);

    /**
     * Close the corresponding WebSocket with a close reason.
     * <p/>
     * This method is used for indicating that underlying connection was closed and/or other condition requires
     * closing socket.
     *
     * @param writer      TODO
     * @param closeCode   close code.
     * @param closeReason close reason.
     */
    void close(SPIWriter writer, int closeCode, String closeReason);

    /**
     * HTTP Upgrade listener.
     */
    interface UpgradeListener {

        /**
         * Called when request is upgraded. The responsibility for making {@link #onConnect(SPIWriter)}
         * call is on listener when it is used.
         */
        void onUpgradeFinished();
    }

    /**
     * Called when response is received from the server.
     */
    interface SPIClientHandshakeListener {

        /**
         * Called when correct handshake response is received.
         *
         * @param headers of the handshake response.
         */
        public void onResponseHeaders(Map<String, String> headers);


        /**
         * Called when an error is found in handshake response.
         *
         * @param exception error found during handshake response check.
         */
        public void onError(Throwable exception);
    }
}
