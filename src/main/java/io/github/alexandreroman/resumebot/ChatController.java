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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {
    private final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final MessageService messageService;
    private final ChatClient chatClient;
    private final AppConfig config;

    ChatController(MessageService messageService, ChatClient.Builder chatClientBuilder, AppConfig config) {
        this.messageService = messageService;
        this.chatClient = chatClientBuilder.build();
        this.config = config;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @RegisterReflectionForBinding(ChatResponse.class)
    String chat(@RequestBody ChatRequest req) {
        if (req.question == null) {
            throw new IllegalArgumentException("Input question cannot be null");
        }
        final var q = req.question.trim();
        if (q.isEmpty()) {
            throw new IllegalArgumentException("Input question cannot be empty");
        }
        return processQuestion(req.conversationId, q);
    }

    private String processQuestion(String conversationId, String question) {
        final var cid = conversationId == null ? "<none>" : conversationId;
        logger.info("Processing question [{}] from conversation {}", question, cid);
        final var resp = chatClient.prompt()
                .system(config.systemPrompt())
                .user(p -> p.text(config.userPrompt())
                        .param("resume", config.resume())
                        .param("question", question)
                        .param("conversation", getConversationHistory(conversationId)))
                .call()
                .entity(ChatResponse.class);
        if (resp == null) {
            throw new IllegalStateException("No response from AI after asking [" + question + "] in conversation " + cid);
        }
        if (!resp.foundAnswer) {
            logger.info("No answer found for question [{}] from conversation {}", question, cid);
        } else {
            logger.info("Found answer for question [{}] from conversation {}: {}", question, cid, resp.answer);

            if (conversationId != null) {
                messageService.addMessage(conversationId, MessageType.USER, question);
                messageService.addMessage(conversationId, MessageType.ASSISTANT, resp.answer);
            }
        }
        return resp.answer;
    }

    private String getConversationHistory(String conversationId) {
        return String.join("\n", messageService.getMessages(conversationId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: " + e.getMessage());
    }

    private record ChatRequest(String question, String conversationId) {
    }

    private record ChatResponse(
            @JsonPropertyDescription("Answer to the question in Markdown, may default to a generic answer if the resume is missing data")
            String answer,
            @JsonPropertyDescription("Set to true if the answer was found in the resume, otherwise set to false if the resume is missing data")
            boolean foundAnswer
    ) {
    }
}
