package com.inspien.order.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Builder
public class Outbox {
    @Setter
    private String orderId;

    private final String userId;

    private final String itemId;

    private final String applicantKey;

    private final String name;

    private final String address;

    private final String itemName;

    private final String price;

    private final String status;

    private final LocalDateTime updated;

    @Builder.Default
    private Boolean processed = false;

}
