package com.example.workconnect.adapters;

import com.example.workconnect.models.ChatItem;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.utils.ChatUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ChatMessageAdapterTest {

    private static final String TEST_USER_ID = "testUserId";

    @Test
    public void testInsertDateSeparators_NullMessages_ReturnsEmptyList() {
        // 1. Setting up the conditions
        List<ChatMessage> messages = null;

        // 2. Calling the function under test
        List<ChatItem> result = ChatUtils.insertDateSeparators(messages);

        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Null messages should return empty list", 0, result.size());
    }

    @Test
    public void testInsertDateSeparators_EmptyList_ReturnsEmptyList() {
        // 1. Setting up the conditions
        List<ChatMessage> messages = new ArrayList<>();

        // 2. Calling the function under test
        List<ChatItem> result = ChatUtils.insertDateSeparators(messages);

        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Empty list should return empty list", 0, result.size());
    }

    @Test
    public void testInsertDateSeparators_SingleMessage_ReturnsSeparatorAndMessage() {
        // 1. Setting up the conditions
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage message = createTestMessage("msg1", new Date());
        messages.add(message);

        // 2. Calling the function under test
        List<ChatItem> result = ChatUtils.insertDateSeparators(messages);

        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Single message should return separator + message", 2, result.size());
        assertTrue("First item should be a date separator", result.get(0).isDateSeparator());
        assertTrue("Second item should be a message", result.get(1).isMessage());
        assertEquals("Message should match", message, result.get(1).getMessage());
    }

    @Test
    public void testInsertDateSeparators_MultipleMessagesSameDay_NoAdditionalSeparators() {
        // 1. Setting up the conditions
        List<ChatMessage> messages = new ArrayList<>();
        Date sameDay = new Date();
        messages.add(createTestMessage("msg1", sameDay));
        messages.add(createTestMessage("msg2", sameDay));
        messages.add(createTestMessage("msg3", sameDay));

        // 2. Calling the function under test
        List<ChatItem> result = ChatUtils.insertDateSeparators(messages);

        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Three messages same day should have 1 separator + 3 messages", 4, result.size());
        assertTrue("First item should be a date separator", result.get(0).isDateSeparator());
        assertTrue("Second item should be a message", result.get(1).isMessage());
        assertTrue("Third item should be a message", result.get(2).isMessage());
        assertTrue("Fourth item should be a message", result.get(3).isMessage());
    }

    @Test
    public void testInsertDateSeparators_MessagesDifferentDays_InsertsSeparators() {
        // 1. Setting up the conditions
        List<ChatMessage> messages = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        Date day1 = cal.getTime();

        cal.add(Calendar.DAY_OF_YEAR, 1);
        Date day2 = cal.getTime();

        cal.add(Calendar.DAY_OF_YEAR, 1);
        Date day3 = cal.getTime();

        messages.add(createTestMessage("msg1", day1));
        messages.add(createTestMessage("msg2", day2));
        messages.add(createTestMessage("msg3", day3));

        // 2. Calling the function under test
        List<ChatItem> result = ChatUtils.insertDateSeparators(messages);

        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Three messages different days should have 3 separators + 3 messages", 6, result.size());
        assertTrue("Item 0 should be separator", result.get(0).isDateSeparator());
        assertTrue("Item 1 should be message", result.get(1).isMessage());
        assertTrue("Item 2 should be separator", result.get(2).isDateSeparator());
        assertTrue("Item 3 should be message", result.get(3).isMessage());
        assertTrue("Item 4 should be separator", result.get(4).isDateSeparator());
        assertTrue("Item 5 should be message", result.get(5).isMessage());
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
        when(mockMessage1.getId()).thenReturn("msg1");

        ChatMessage mockMessage2 = mock(ChatMessage.class);
        when(mockMessage2.getSentAt()).thenReturn(day2);
        when(mockMessage2.getId()).thenReturn("msg2");

        messages.add(mockMessage1);
        messages.add(mockMessage2);

        // 2. Calling the function under test
        List<ChatItem> result = ChatUtils.insertDateSeparators(messages);

        // 3. Assertions to verify the expected result
        assertNotNull("Result should not be null", result);
        assertEquals("Two messages different days should have 2 separators + 2 messages", 4, result.size());
        assertTrue("Item 0 should be separator", result.get(0).isDateSeparator());
        assertTrue("Item 1 should be message", result.get(1).isMessage());
        assertTrue("Item 2 should be separator", result.get(2).isDateSeparator());
        assertTrue("Item 3 should be message", result.get(3).isMessage());

        verify(mockMessage1, times(3)).getSentAt();
        verify(mockMessage2, atLeastOnce()).getSentAt();
    }

    private ChatMessage createTestMessage(String id, Date sentAt) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setSenderId(TEST_USER_ID);
        message.setText("Test message " + id);
        message.setSentAt(sentAt);
        message.setStatus(ChatMessage.MessageStatus.SENT);
        return message;
    }
}
