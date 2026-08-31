package com.afriland.audiences.model.chat;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder // Using builder pattern for easier object creation
public class ChatMessage {

    private MessageType type; // Enum to define message type (CHAT, JOIN, LEAVE)
    private String content;
    private String sender; // Username of the sender
    private String recipient; // Username of the recipient (for private messages)
    private Instant timestamp;

    public enum MessageType {
        CHAT,
        JOIN, // User joining the chat
        LEAVE // User leaving the chat
    }

    // Default constructor (needed for some serialization libraries)
    public ChatMessage() {
        this.timestamp = Instant.now();
    }

    // All-args constructor for the builder
    public ChatMessage(MessageType type, String content, String sender, String recipient, Instant timestamp) {
        this.type = type;
        this.content = content;
        this.sender = sender;
        this.recipient = recipient;
        this.timestamp = (timestamp != null) ? timestamp : Instant.now();
    }
}