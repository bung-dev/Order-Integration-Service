package com.inspien.order.strategy;

import com.inspien.common.config.properties.AppProperties;
import com.inspien.common.exception.CustomException;
import com.inspien.common.exception.ErrorCode;
import com.inspien.order.domain.Order;
import com.inspien.order.service.OrderCommandService;
import com.inspien.receiver.sftp.FileWriter;
import com.inspien.receiver.sftp.SftpUploader;
import com.inspien.sender.dto.CreateOrderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncOrderProcessorTest {

    @InjectMocks
    private SyncOrderProcessor syncOrderProcessor;

    @Mock private OrderCommandService orderCommandService;
    @Mock private FileWriter fileWriter;
    @Mock private SftpUploader sftpUploader;
    @Mock private AppProperties appProperties;

    @Test
    @DisplayName("파일 생성 및 SFTP 업로드 성공 테스트")
    void process_Success() throws Exception {
        List<Order> orders = List.of(Order.builder().userId("user1").build());
        Path mockPath = Paths.get("test.txt");

        when(appProperties.getParticipantName()).thenReturn("이중호");
        when(fileWriter.write(anyList(), any())).thenReturn(mockPath);
        when(orderCommandService.saveOrdersWithRetry(any(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    Consumer<List<Order>> action = invocation.getArgument(2);
                    action.accept(orders);
                    return CreateOrderResult.ok(1, 0, 1);
                });

        CreateOrderResult result = syncOrderProcessor.process(orders, 0);

        assertTrue(result.success());
        assertEquals(1, result.orderCount());
        verify(sftpUploader, times(1)).upload(any());
    }

    @Test
    @DisplayName("SFTP 전송 실패 시 파일 삭제 및 예외 처리 테스트")
    void process_SftpFail_DeletesFile() throws Exception {
        List<Order> orders = List.of(Order.builder().userId("user1").build());
        Path tempFile = Paths.get("./out/test_file.txt");
        Files.createDirectories(tempFile.getParent());
        if (!Files.exists(tempFile)) Files.createFile(tempFile);

        when(appProperties.getParticipantName()).thenReturn("이중호");
        when(fileWriter.write(anyList(), any())).thenReturn(tempFile);
        doThrow(new RuntimeException("SFTP Fail")).when(sftpUploader).upload(any());
        when(orderCommandService.saveOrdersWithRetry(any(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    Consumer<List<Order>> action = invocation.getArgument(2);
                    action.accept(orders);
                    return null; // 도달하지 않음
                });

        CustomException ex = assertThrows(CustomException.class,
                () -> syncOrderProcessor.process(orders, 0));

        assertEquals(ErrorCode.SFTP_SEND_FAIL, ex.getErrorCode());
        assertFalse(Files.exists(tempFile), "실패 시 생성된 파일이 삭제되어야 함");
    }

    @Test
    @DisplayName("빈 주문 목록 시 VALIDATION_ERROR 예외 발생")
    void process_EmptyOrders_ThrowsValidationError() {
        CustomException ex = assertThrows(CustomException.class,
                () -> syncOrderProcessor.process(List.of(), 0));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }
}
