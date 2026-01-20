package com.example.workconnect.utils;

import com.example.workconnect.models.ChatMessage;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SystemMessageHelper {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static void createSystemMessage(
            String conversationId,
            ChatMessage.SystemMessageType systemType,
            String systemUserId,
            String text) {
        createSystemMessage(conversationId, systemType, systemUserId, null, text);
    }
    
    public static void createSystemMessage(
            String conversationId,
            ChatMessage.SystemMessageType systemType,
            String systemUserId,
            String actorUserId,
            String text) {

        ChatMessage systemMsg = new ChatMessage();
        systemMsg.setConversationId(conversationId);
        systemMsg.setSenderId("system"); // System messages don't have a real sender
        systemMsg.setText(text);
        systemMsg.setSentAt(new Date());
        systemMsg.setRead(false);
        systemMsg.setReadAt(null);
        systemMsg.setStatus(ChatMessage.MessageStatus.SENT);
        systemMsg.setMessageType(ChatMessage.MessageType.SYSTEM);
        systemMsg.setSystemType(systemType);
        systemMsg.setSystemUserId(systemUserId);
        systemMsg.setSystemActorId(actorUserId);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("conversationId", conversationId);
        messageData.put("senderId", "system");
        messageData.put("text", text);
        messageData.put("sentAt", new Date());
        messageData.put("isRead", false);
        messageData.put("readAt", null);
        messageData.put("status", "SENT");
        messageData.put("messageType", "SYSTEM");
        messageData.put("systemType", systemType.name());
        messageData.put("systemUserId", systemUserId);
        if (actorUserId != null) {
            messageData.put("systemActorId", actorUserId);
        }
        messageData.put("readBy", new java.util.ArrayList<String>());

        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .add(messageData);
    }
}
