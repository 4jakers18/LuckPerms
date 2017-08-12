/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.messaging;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import me.lucko.luckperms.common.buffers.BufferedRequest;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * An abstract implementation of {@link me.lucko.luckperms.api.MessagingService}.
 */
@RequiredArgsConstructor
public abstract class AbstractMessagingService implements InternalMessagingService {
    public static final String CHANNEL = "lpuc";

    @Getter
    private final LuckPermsPlugin plugin;

    @Getter
    private final String name;

    private final Set<UUID> receivedMsgs = Collections.synchronizedSet(new HashSet<>());

    @Getter
    private final BufferedRequest<Void> updateBuffer = new BufferedRequest<Void>(10000L, r -> getPlugin().doAsync(r)) {
        @Override
        protected Void perform() {
            pushUpdate();
            return null;
        }
    };

    protected abstract void sendMessage(String channel, String message);

    protected void onMessage(String channel, String msg, Consumer<UUID> callback) {
        if (!channel.equals(CHANNEL)) {
            return;
        }

        UUID uuid = parseUpdateMessage(msg);
        if (uuid == null) {
            return;
        }

        if (!receivedMsgs.add(uuid)) {
            return;
        }

        plugin.getLog().info("[" + name + " Messaging] Received update ping with id: " + uuid.toString());

        if (plugin.getApiProvider().getEventFactory().handleNetworkPreSync(false, uuid)) {
            return;
        }

        plugin.getUpdateTaskBuffer().request();

        if (callback != null) {
            callback.accept(uuid);
        }
    }

    @Override
    public void pushUpdate() {
        plugin.doAsync(() -> {
            UUID id = generateId();
            plugin.getLog().info("[" + name + " Messaging] Sending ping with id: " + id.toString());

            sendMessage(CHANNEL, "update:" + id.toString());
        });
    }

    private UUID generateId() {
        UUID uuid = UUID.randomUUID();
        receivedMsgs.add(uuid);
        return uuid;
    }

    private static UUID parseUpdateMessage(String msg) {
        if (!msg.startsWith("update:")) {
            return null;
        }

        String requestId = msg.substring("update:".length());
        try {
            return UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
