package com.inspien.order.service;

import com.inspien.common.config.properties.AppProperties;
import com.inspien.common.exception.CustomException;
import com.inspien.common.exception.ErrorCode;
import com.inspien.mapper.OrderMapper;
import com.inspien.mapper.OrderParserXML;
import com.inspien.mapper.OrderRequestValidator;
import com.inspien.mapper.dto.OrderHeaderXml;
import com.inspien.mapper.dto.OrderItemXml;
import com.inspien.mapper.dto.OrderRequestXML;
import com.inspien.order.domain.Order;
import com.inspien.receiver.jdbc.OrderRepository;
import com.inspien.receiver.jdbc.OutboxRepository;
import com.inspien.receiver.sftp.FileWriter;
import com.inspien.receiver.sftp.SftpUploader;
import com.inspien.sender.dto.CreateOrderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private OrderParserXML orderParserXML;
    @Mock private OrderRequestValidator validator;
    @Mock private OrderMapper mapper;
    @Mock private IdGenerator idGenerator;
    @Mock private FileWriter fileWriter;
    @Mock private SftpUploader sftpUploader;
    @Mock private PlatformTransactionManager txManager;
    @Mock private AppProperties appProperties;

    @Test
    @DisplayName("주문 생성 전체 프로세스 성공 테스트")
    void createOrderSync_Success() throws Exception {
        // given
        String base64Xml = "dGVzdCB4bWw=";
        OrderRequestXML request = new OrderRequestXML();
        OrderHeaderXml h = new OrderHeaderXml();
        h.setUserId("user1"); h.setName("Name"); h.setAddress("Addr"); h.setStatus("N");
        request.setHeaders(List.of(h));
        OrderItemXml item = new OrderItemXml();
        item.setUserId("user1"); item.setItemId("I001"); item.setItemName("Item"); item.setPrice("1000");
        request.setItems(List.of(item));

        List<Order> orders = List.of(Order.builder().userId("user1").build());
        Path mockPath = Paths.get("test.txt");

        when(orderParserXML.parse(anyString())).thenReturn(request);
        when(mapper.flatten(any())).thenReturn(new com.inspien.mapper.dto.FlattenResult(orders, 0));
        when(idGenerator.generate()).thenReturn("A001");
        when(orderRepository.batchInsert(any())).thenReturn(new int[]{1});
        when(fileWriter.write(anyList(), any())).thenReturn(mockPath);
        when(appProperties.getMaxRetry()).thenReturn(5);
        when(appProperties.getParticipantName()).thenReturn("이중호");

        // when
        CreateOrderResult result = orderService.createOrderSync(base64Xml);

        // then
        assertTrue(result.success());
        assertEquals(1, result.retryCount());
        assertEquals(1, result.orderCount());
        verify(sftpUploader, times(1)).upload(any());
    }

    @Test
    @DisplayName("SFTP 전송 실패 시 파일 삭제 및 예외 처리 테스트")
    void saveOrders_SftpUploadFail_RollbackFile() throws Exception {
        // given
        List<Order> orders = List.of(Order.builder().userId("user1").build());
        Path tempFile = Paths.get("./out/test_file.txt");
        Files.createDirectories(tempFile.getParent());
        if (!Files.exists(tempFile)) Files.createFile(tempFile);

        when(appProperties.getMaxRetry()).thenReturn(1);
        when(appProperties.getParticipantName()).thenReturn("이중호");
        when(orderRepository.batchInsert(any())).thenReturn(new int[]{1});
        when(fileWriter.write(anyList(), any())).thenReturn(tempFile);
        doThrow(new RuntimeException("SFTP Fail")).when(sftpUploader).upload(any());

        // when & then
        CustomException ex = assertThrows(CustomException.class, () ->
                ReflectionTestUtils.invokeMethod(orderService, "saveOrders", orders, 0)
        );

        assertEquals(ErrorCode.SFTP_SEND_FAIL, ex.getErrorCode());
        assertFalse(Files.exists(tempFile), "실패 시 생성된 파일이 삭제되어야 함");
    }

    @Test
    @DisplayName("DB 중복 키 발생 시 재시도 로직 테스트")
    void saveOrders_DuplicateKeyRetry() {
        // given
        List<Order> orders = List.of(Order.builder().userId("user1").build());
        when(appProperties.getMaxRetry()).thenReturn(3);
        when(appProperties.getParticipantName()).thenReturn("이중호");

        when(orderRepository.batchInsert(any()))
                .thenThrow(new DuplicateKeyException("Duplicate"))
                .thenThrow(new DuplicateKeyException("Duplicate"))
                .thenReturn(new int[]{1});
        when(fileWriter.write(any(), any())).thenReturn(Paths.get("success.txt"));

        // when
        CreateOrderResult result = ReflectionTestUtils.invokeMethod(orderService, "saveOrders", orders, 0);

        // then
        assertTrue(result.success());
        assertEquals(3, result.retryCount());
    }

    @Test
    @DisplayName("아웃박스 패턴 주문 생성 테스트")
    void createOrderOutbox_Success() throws Exception {
        // given
        String base64Xml = "dGVzdCB4bWw=";
        OrderRequestXML request = new OrderRequestXML();
        OrderHeaderXml h = new OrderHeaderXml();
        h.setUserId("user1"); h.setName("Name"); h.setAddress("Addr"); h.setStatus("N");
        request.setHeaders(List.of(h));
        OrderItemXml item = new OrderItemXml();
        item.setUserId("user1"); item.setItemId("I001"); item.setItemName("Item"); item.setPrice("1000");
        request.setItems(List.of(item));

        List<Order> orders = List.of(Order.builder().userId("user1").applicantKey("key").build());

        when(orderParserXML.parse(anyString())).thenReturn(request);
        when(mapper.flatten(any())).thenReturn(new com.inspien.mapper.dto.FlattenResult(orders, 0));
        when(idGenerator.generate()).thenReturn("A001");
        when(orderRepository.batchInsert(any())).thenReturn(new int[]{1});
        when(outboxRepository.batchInsert(anyList())).thenReturn(new int[]{1});
        when(appProperties.getMaxRetry()).thenReturn(5);

        // when
        CreateOrderResult result = orderService.createOrderOutbox(base64Xml);

        // then
        assertTrue(result.success());
        assertEquals(1, result.orderCount());
        verify(outboxRepository, times(1)).batchInsert(anyList());
        verify(sftpUploader, never()).upload(any());
    }
}
