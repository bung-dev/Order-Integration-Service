package com.inspien.order.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Outbox {
    @Setter
    private String orderId;

    private String userId;

    private String itemId;

    private String applicantKey;

    private String name;

    private String address;

    private String itemName;

    private String price;

    @Builder.Default
    private String status = "UNPROCESSED";

    private LocalDateTime updated;

    @Builder.Default
    private Boolean processed = false;

    @Setter
    @Builder.Default
    private int retryCount = 0;

    @Setter
    private String lastErrorMsg;

}
