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

        Status status = resolveStatus(e, traceId);
        responseObserver.onError(status.asRuntimeException());
    }

    private static Status resolveStatus(Exception e, String traceId) {
        if (e instanceof NotFoundException) {
            log.warn("gRPC client error [NotFound]: {}", e.getMessage());
            return Status.NOT_FOUND.withDescription(e.getMessage());
        }
        if (e instanceof IllegalArgumentException || e instanceof java.time.DateTimeException) {
            log.warn("gRPC client error [InvalidArgument]: {}", e.getMessage());
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        }
        if (e instanceof DomainException) {
            log.warn("gRPC client error [FailedPrecondition]: {}", e.getMessage());
            return Status.FAILED_PRECONDITION.withDescription(e.getMessage());
        }
        log.error("gRPC unexpected internal error [traceId={}]: {}", traceId, e.getMessage(), e);
        return Status.INTERNAL.withDescription("An unexpected internal server error occurred. Ref: " + traceId);
    }
}
