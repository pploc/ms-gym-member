package com.gym.member.member.domain.exception;

import com.gym.common.error.CommonErrorCode;
import com.gym.common.error.DomainException;

public class InactivePlanException extends DomainException {
    public InactivePlanException(String message) {
        super(CommonErrorCode.VALIDATION_FAILED, message);
    }
}
