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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {
    private final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final ChatTools tools;
    private final MessageService messageService;
    private final ChatClient chatClient;
    private final AppConfig config;

    ChatController(ChatTools tools, MessageService messageService, ChatClient.Builder chatClientBuilder, AppConfig config) {
        this.tools = tools;
        this.messageService = messageService;
        this.chatClient = chatClientBuilder.build();
        this.config = config;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @RegisterReflectionForBinding(ChatResponse.class)
    String chat(@RequestParam("prompt") String prompt,
                @RequestParam(value = "conversationId", required = false) String conversationId) {
        if (prompt == null) {
            throw new IllegalArgumentException("Input prompt cannot be null");
        }
        final var p = prompt.trim();
        if (p.isEmpty()) {
            throw new IllegalArgumentException("Input prompt cannot be empty");
        }
        return processPrompt(conversationId, p);
    }

    private String processPrompt(String conversationId, String prompt) {
        final var cid = conversationId == null ? "<none>" : conversationId;
        logger.info("Processing prompt [{}] from conversation {}", prompt, cid);

        final var outputConverter = new BeanOutputConverter<ChatResponse>(ChatResponse.class);
        final var jsonSchema = outputConverter.getJsonSchema();

        // Use OpenAiChatOptions to enforce the use of JSON for output (following the
        // JSON schema).
        final var strResp = chatClient.prompt()
                .system(config.systemPrompt())
                .user(u -> u.text(config.userPrompt())
                        .param("resume", config.resume())
                        .param("prompt", prompt)
                        .param("conversation", getConversationHistory(conversationId)))
                .tools(tools)
                .options(OpenAiChatOptions.builder().outputSchema(jsonSchema).build())
                .call().content();
        if (strResp == null) {
            throw new IllegalStateException(
                    "No response from AI after asking [" + prompt + "] in conversation " + cid);
        }
        final var resp = outputConverter.convert(strResp);
        if (!resp.foundAnswer) {
            logger.info("No answer found for prompt [{}] from conversation {}", prompt, cid);
        } else {
            logger.info("Found answer for prompt [{}] from conversation {}: {}", prompt, cid, resp.answer);

            if (conversationId != null) {
                messageService.addMessage(conversationId, MessageType.USER, prompt);
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

    private record ChatResponse(
            @JsonProperty(value = "answer", required = true) @JsonPropertyDescription("Answer to the question in Markdown, may default to a generic answer if the resume is missing data") String answer,
            @JsonProperty(value = "foundAnswer", required = true) @JsonPropertyDescription("Set to true if the answer was found in the resume, otherwise set to false if the resume is missing data") boolean foundAnswer) {
    }
}
