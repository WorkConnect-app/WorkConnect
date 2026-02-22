package com.example.workconnect.utils;

import com.example.workconnect.models.ChatItem;
import com.example.workconnect.models.ChatMessage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ChatUtilsTest {

    @Test
    public void testIsDifferentDay_SameDay_ReturnsFalse() {
        // 1. Setting up the conditions
        Calendar cal = Calendar.getInstance();
        Date date1 = cal.getTime();
        Date date2 = cal.getTime();
        
        // 2. Calling the function under test
        boolean result = DateHelper.isDifferentDay(date1, date2);
        
        // 3. Assertions to verify the expected result
        assertFalse("Two dates on the same day should return false", result);
    }

    @Test
    public void testFindOtherParticipantId_TwoParticipants_ReturnsOtherUserId() {
        // 1. Setting up the conditions
        List<String> participantIds = Arrays.asList("user1", "user2");
        String currentUserId = "user1";
        
        // 2. Calling the function under test
        String result = ChatUtils.findOtherParticipantId(participantIds, currentUserId);
        
        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Should return the other participant ID", "user2", result);
    }

    @Test
    public void testInsertDateSeparators_MockedMessagesDifferentDays_InsertsSeparators() {
        // 1. Setting up the conditions
        List<ChatMessage> messages = new ArrayList<>();
        
        Calendar cal = Calendar.getInstance();
        Date day1 = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        Date day2 = cal.getTime();
        
        ChatMessage mockMessage1 = mock(ChatMessage.class);
        when(mockMessage1.getSentAt()).thenReturn(day1);
        messages.add(mockMessage1);
        
        ChatMessage mockMessage2 = mock(ChatMessage.class);
        when(mockMessage2.getSentAt()).thenReturn(day2);
        messages.add(mockMessage2);
        
        // 2. Calling the function under test
        List<ChatItem> result = ChatUtils.insertDateSeparators(messages);
        
        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Two messages on different days should have 2 separators + 2 messages", 4, result.size());
        assertTrue("First item should be a date separator", result.get(0).isDateSeparator());
        assertTrue("Second item should be a message", result.get(1).isMessage());
        assertTrue("Third item should be a date separator", result.get(2).isDateSeparator());
        assertTrue("Fourth item should be a message", result.get(3).isMessage());
    }
}
