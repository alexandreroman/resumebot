/*
 * Copyright (c) 2026 Alexandre Roman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.alexandreroman.resumebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
class MessageService {
    private final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private final StringRedisTemplate redis;

    MessageService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String getMessagesKey(String conversationId) {
        return String.format("resumebot::conversations::%s::messages", conversationId);
    }

    void addMessage(String conversationId, MessageType messageType, String message) {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId is null");
        }

        final var key = getMessagesKey(conversationId);
        final var m = (messageType.equals(MessageType.USER) ? "Q: " : "A: ") + message;
        logger.debug("Adding message to conversation {}: {}", conversationId, m);
        redis.opsForList().rightPush(key, message);
        redis.expire(key, 1, TimeUnit.DAYS);
    }

    List<String> getMessages(String conversationId) {
        if (conversationId == null) {
            return List.of();
        }

        final var messages = (List<?>) redis.opsForList().range(getMessagesKey(conversationId), 0, -1);
        if (messages == null) {
            return List.of();
        }
        return (List<String>) messages;
    }
}
