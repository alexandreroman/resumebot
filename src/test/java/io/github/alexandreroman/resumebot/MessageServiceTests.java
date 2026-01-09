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

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MessageServiceTests {
    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("8.4"));

    @Autowired
    private MessageService messageService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void messageHistory() {
        final var cid = "testfoo";
        deleteConversation(cid);

        final var keyPattern = "resumebot::conversations::" + cid + "::messages";
        messageService.addMessage(cid, MessageType.USER, "Hello");
        messageService.addMessage(cid, MessageType.ASSISTANT, "Hey");

        final var history = messageService.getMessages(cid);
        assertThat(history).hasSize(2);
        assertThat(history).containsExactly("Q: Hello", "A: Hey");
    }

    private void deleteConversation(String cid) {
        final var keys = redisTemplate.keys("resumebot::conversations::*");
        if (keys == null) {
            return;
        }
        for (final var key : keys) {
            redisTemplate.delete(key);
        }
    }
}
