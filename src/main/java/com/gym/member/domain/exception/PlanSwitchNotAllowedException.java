package com.gym.member.domain.exception;

import com.gym.common.error.CommonErrorCode;
import com.gym.common.error.DomainException;

public class PlanSwitchNotAllowedException extends DomainException {
    public PlanSwitchNotAllowedException(String message) {
        super(CommonErrorCode.VALIDATION_FAILED, message);
    }
}
