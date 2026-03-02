package com.inspien.receiver.sftp;

import com.inspien.common.config.properties.AppProperties;
import com.inspien.common.exception.ErrorCode;
import com.inspien.order.domain.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileWriter {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AppProperties appProperties;

    public Path write(List<Order> orders, String participantName) {
        try {
            Path dir = Paths.get(appProperties.getOutDir());
            Files.createDirectories(dir);

            String filename = "INSPIEN_[" + participantName + "]_[" + LocalDateTime.now().format(FMT) + "].txt";
            Path file = dir.resolve(filename);

            List<Order> sorted = orders.stream()
                    .sorted(Comparator.comparing(
                            Order::getOrderId,
                            Comparator.nullsLast(String::compareTo)
                    ))
                    .toList();

            StringBuilder sb = new StringBuilder();
            for (Order o : sorted) {
                sb.append(o.getOrderId()).append("^")
                        .append(o.getUserId()).append("^")
                        .append(o.getItemId()).append("^")
                        .append(o.getApplicantKey()).append("^")
                        .append(o.getName()).append("^")
                        .append(o.getAddress()).append("^")
                        .append(o.getItemName()).append("^")
                        .append(o.getPrice())
                        .append("\n");
            }
            Path written = Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[FILE] created file={} rows={}", written.getFileName(), sorted.size());

            return written;

        } catch (IOException e) {
            log.error("[FILE] write_fail reason=io_error participantName={}", participantName, e);
            throw ErrorCode.FILE_WRITE_FAIL.exception();
        } catch (InvalidPathException e) {
            log.error("[FILE] write_fail reason=invalid_path participantName={}", participantName, e);
            throw ErrorCode.INVALID_PATH.exception();
        }
    }
}
