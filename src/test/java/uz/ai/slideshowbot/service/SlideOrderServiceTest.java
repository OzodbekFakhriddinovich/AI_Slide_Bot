package uz.ai.slideshowbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.ai.slideshowbot.entity.*;
import uz.ai.slideshowbot.enums.UserState;
import uz.ai.slideshowbot.repository.*;
import uz.ai.slideshowbot.slides.SlideRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlideOrderServiceTest {
    private UserRepository users;
    private SlideOrderRepository orders;
    private SlideOrderService service;
    private UserEntity user;

    @BeforeEach void setup() {
        users = mock(UserRepository.class); orders = mock(SlideOrderRepository.class);
        service = new SlideOrderService(users, orders);
        user = new UserEntity(); user.setChatId(42L); user.setBalance(10000); user.setState(UserState.START);
        when(users.lockSlideUser(42L)).thenReturn(Optional.of(user));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SlideRequest request(int price) {
        return new SlideRequest("Tarmoq xavfsizligi", "Name", "UZ", 10, "auto", price, "pptx", "request_123456");
    }

    @Test void chargesOnceAndCreatesPersistentReceipt() {
        SlideOrderEntity order = service.reserve(42L, request(5000));
        assertEquals(5000, user.getBalance()); assertEquals(UserState.CREATING_SLIDES, user.getState());
        assertEquals("RUNNING", order.getStatus()); assertEquals(5000, order.getCost());
        assertNotNull(order.getId()); assertEquals("request_123456", order.getRequestKey());
    }

    @Test void duplicatesAndActiveOrdersNeverCharge() {
        when(orders.findByChatIdAndRequestKey(42L, "request_123456")).thenReturn(Optional.of(new SlideOrderEntity()));
        assertThrows(IllegalArgumentException.class, () -> service.reserve(42L, request(5000)));
        assertEquals(10000, user.getBalance()); verify(users, never()).save(any());
        when(orders.findByChatIdAndRequestKey(42L, "request_123456")).thenReturn(Optional.empty());
        when(orders.existsByChatIdAndStatus(42L, "RUNNING")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.reserve(42L, request(5000)));
        verify(orders, never()).save(any());
    }

    @Test void insufficientBalanceAndInvalidTariffsNeverCreateAnOrder() {
        user.setBalance(1000);
        SlideOrderService.InsufficientBalanceException error = assertThrows(SlideOrderService.InsufficientBalanceException.class, () -> service.reserve(42L, request(7000)));
        assertEquals(1000, error.balance()); assertEquals(7000, error.required());
        assertThrows(IllegalArgumentException.class, () -> service.reserve(42L, request(3000)));
        assertThrows(IllegalArgumentException.class, () -> service.reserve(42L, request(-3000)));
        verify(orders, never()).save(any()); verify(users, never()).save(any());
    }

    @Test void refundsOnlyOnceWithoutSavingAnyStaleUserEntity() {
        SlideOrderEntity order = service.reserve(42L, request(5000));
        clearInvocations(users);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        assertTrue(service.refund(order.getId())); assertFalse(service.refund(order.getId()));
        verify(users, times(1)).creditSlideRefund(42L, 5000);
        verify(users, times(1)).finishSlideState(42L, UserState.CREATING_SLIDES, UserState.START);
        verify(users, never()).save(any()); assertEquals("REFUNDED", order.getStatus());
    }

    @Test void successfulDeliveryAndRecentWorkAreNotRefunded() {
        SlideOrderEntity order = service.reserve(42L, request(5000));
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        assertFalse(service.expire(order.getId(), LocalDateTime.now().minusMinutes(30)));
        assertTrue(service.complete(order.getId())); assertFalse(service.refund(order.getId()));
        verify(users, never()).creditSlideRefund(anyLong(), anyInt());
    }
}
