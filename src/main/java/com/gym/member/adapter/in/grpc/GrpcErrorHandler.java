package com.gym.member.adapter.in.grpc;

import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

/**
 * Completes the stream on success. Errors propagate to common-java ExceptionInterceptor.
 */
public final class GrpcErrorHandler {

    private GrpcErrorHandler() {}

    public static <T> void execute(StreamObserver<T> responseObserver, Supplier<T> action) {
        T response = action.get();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
