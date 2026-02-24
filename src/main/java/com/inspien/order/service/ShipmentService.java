package com.inspien.order.service;

import com.inspien.order.domain.Order;
import com.inspien.order.domain.Outbox;
import com.inspien.receiver.jdbc.OrderRepository;
import com.inspien.receiver.jdbc.OutboxRepository;
import com.inspien.receiver.jdbc.ShipmentRepository;
import com.inspien.receiver.jdbc.dto.PendingOrderRow;
import com.inspien.receiver.jdbc.dto.ShipmentRow;
import com.inspien.receiver.sftp.FileWriter;
import com.inspien.receiver.sftp.SftpUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final FileWriter fileWriter;
    private final SftpUploader sftpUploader;

    @Value("${order.participant-name}")
    private String participantName;

    @Transactional
    public void run(String applicantKey) {
        List<PendingOrderRow> pending = orderRepository.findPendingForShipment(applicantKey);
        if (pending.isEmpty()) {
            log.info("[BATCH] no_pending");
            return;
        }

        List<ShipmentRow> shipmentRows = pending.stream()
                .map(p -> new ShipmentRow(
                        p.orderId(),
                        p.orderId(),
                        p.itemId(),
                        p.applicantKey(),
                        p.address()
                ))
                .toList();

        int[] results = shipmentRepository.batchInsert(shipmentRows);

        List<String> successOrderIds = new ArrayList<>();
        for (int i = 0; i < results.length; i++) {
            int r = results[i];
            if (r == 1 || r == -2) {
                successOrderIds.add(shipmentRows.get(i).orderId());
            }
        }

        int updated = orderRepository.updateStatusToY(applicantKey, successOrderIds);
        log.info("[BATCH] pending={}, inserted={}, updated={}",
                pending.size(), successOrderIds.size(), updated);
    }

    @Transactional
    public void runOutbox(String applicantKey) {
        List<Outbox> outboxes = outboxRepository.findUnprocessed(applicantKey);
        if (outboxes.isEmpty()) {
            log.info("[BATCH OUTBOX] no_pending");
            return;
        }

        log.info("[BATCH OUTBOX] found={}", outboxes.size());

        List<Order> orders = outboxes.stream()
                .map(o -> Order.builder()
                        .applicantKey(o.getApplicantKey())
                        .orderId(o.getOrderId())
                        .userId(o.getUserId())
                        .itemId(o.getItemId())
                        .name(o.getName())
                        .address(o.getAddress())
                        .itemName(o.getItemName())
                        .price(o.getPrice())
                        .status(o.getStatus())
                        .build())
                .toList();

        try {
            Path file = fileWriter.write(orders, participantName);
            log.info("[BATCH OUTBOX:FILE] created file={}", file.getFileName());

            sftpUploader.upload(file);
            log.info("[BATCH OUTBOX:SFTP] upload_ok file={}", file.getFileName());

            List<String> orderIds = outboxes.stream().map(Outbox::getOrderId).toList();
            int updated = outboxRepository.updateProcessed(applicantKey, orderIds);
            log.info("[BATCH OUTBOX] processed={}", updated);

        } catch (Exception e) {
            log.error("[BATCH OUTBOX] process_fail", e);
        }
    }

    @Transactional
    public void cleanupProcessedOutbox(String applicantKey) {
        int deleted = outboxRepository.deleteProcessed(applicantKey);
        log.info("[BATCH OUTBOX] cleaned_up deleted={}", deleted);
    }
}
