package com.gym.member.member.domain.exception;

import com.gym.common.error.CommonErrorCode;
import com.gym.common.error.DomainException;

public class CannotPauseLifetimeException extends DomainException {
    public CannotPauseLifetimeException(String message) {
        super(CommonErrorCode.VALIDATION_FAILED, message);
    }
}
