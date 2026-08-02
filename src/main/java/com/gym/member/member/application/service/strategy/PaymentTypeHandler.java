package com.gym.member.member.application.service.strategy;

import com.gym.proto.events.v1.PaymentCompletedEvent;

public interface PaymentTypeHandler {
    boolean supports(String paymentType);
    void handle(PaymentCompletedEvent event, String fallbackKey);
}
