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

    private String status;

    private LocalDateTime updated;

    @Builder.Default
    private Boolean processed = false;

}
