package com.gym.member.adapter.in.grpc;

import com.gym.common.error.DomainException;
import com.gym.common.error.NotFoundException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

@Slf4j
public final class GrpcErrorHandler {

    private GrpcErrorHandler() {}

    public static <T> void execute(StreamObserver<T> responseObserver, java.util.function.Supplier<T> action) {
        try {
            T response = action.get();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleError(responseObserver, e);
        }
    }

    public static void handleError(StreamObserver<?> responseObserver, Exception e) {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        Status status;
        if (e instanceof NotFoundException) {
            log.warn("gRPC client error [NotFound]: {}", e.getMessage());
            status = Status.NOT_FOUND.withDescription(e.getMessage());
        } else if (e instanceof IllegalArgumentException || e instanceof java.time.DateTimeException) {
            log.warn("gRPC client error [InvalidArgument]: {}", e.getMessage());
            status = Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        } else if (e instanceof DomainException) {
            log.warn("gRPC client error [FailedPrecondition]: {}", e.getMessage());
            status = Status.FAILED_PRECONDITION.withDescription(e.getMessage());
        } else {
            log.error("gRPC unexpected internal error [traceId={}]: {}", traceId, e.getMessage(), e);
            status = Status.INTERNAL.withDescription("An unexpected internal server error occurred. Ref: " + traceId);
        }
        responseObserver.onError(status.asRuntimeException());
    }
}
