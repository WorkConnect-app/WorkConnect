package com.example.workconnect.adapters;

import com.example.workconnect.models.ChatConversation;
import com.example.workconnect.utils.ChatUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

public class ChatConversationAdapterTest {

    private static final String CURRENT_USER_ID = "user1";

    @Test
    public void testGetOtherParticipantId_TwoParticipants_ReturnsOtherUserId() {
        // 1. Setting up the conditions
        ChatConversation mockConversation = mock(ChatConversation.class);
        List<String> participantIds = Arrays.asList("user1", "user2");
        when(mockConversation.getParticipantIds()).thenReturn(participantIds);
        
        // 2. Calling the function under test
        String result = ChatUtils.findOtherParticipantId(mockConversation.getParticipantIds(), CURRENT_USER_ID);
        
        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Should return the other participant ID", "user2", result);
    }

    @Test
    public void testGetOtherParticipantId_ConversationNull_ReturnsNull() {
        // 1. Setting up the conditions
        List<String> participantIds = null;
        
        // 2. Calling the function under test
        String result = ChatUtils.findOtherParticipantId(participantIds, CURRENT_USER_ID);
        
        // 3. Assertions to verify the expected result
        assertNull("When participantIds is null, should return null", result);
    }

    @Test
    public void testGetOtherParticipantId_ParticipantIdsNull_ReturnsNull() {
        // 1. Setting up the conditions
        List<String> participantIds = null;
        
        // 2. Calling the function under test
        String result = ChatUtils.findOtherParticipantId(participantIds, CURRENT_USER_ID);
        
        // 3. Assertions to verify the expected result
        assertNull("When participantIds is null, should return null", result);
    }

    @Test
    public void testGetOtherParticipantId_NoOtherParticipant_ReturnsNull() {
        // 1. Setting up the conditions
        List<String> participantIds = Arrays.asList("user1");
        
        // 2. Calling the function under test
        String result = ChatUtils.findOtherParticipantId(participantIds, CURRENT_USER_ID);
        
        // 3. Assertions to verify the expected result
        assertNull("When no other participant exists, should return null", result);
    }

    @Test
    public void testGetOtherParticipantId_MultipleParticipants_ReturnsFirstOtherUserId() {
        // 1. Setting up the conditions
        List<String> participantIds = Arrays.asList("user1", "user2", "user3");
        
        // 2. Calling the function under test
        String result = ChatUtils.findOtherParticipantId(participantIds, CURRENT_USER_ID);
        
        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Should return the first other participant ID", "user2", result);
        assertNotEquals("Should not return current user ID", CURRENT_USER_ID, result);
    }
}
