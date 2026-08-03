package com.gym.member.unit.outbox;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.gym.member.shared.outbox.service.OutboxPayloadParser;
import com.gym.proto.events.v1.MembershipActivatedEvent;
import com.gym.proto.events.v1.MembershipExpiredEvent;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import com.gym.proto.events.v1.MembershipPausedEvent;
import com.gym.proto.events.v1.MembershipResumedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OutboxPayloadParserUnitTest {

    private OutboxPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new OutboxPayloadParser();
    }

    @Test
    void givenRegisteredEvents_whenParsed_returnsProtobufMessages() throws Exception {
        MembershipActivatedEvent activated = MembershipActivatedEvent.newBuilder().setMemberId("mem-1").build();
        String json1 = JsonFormat.printer().print(activated);
        Message p1 = parser.parse("events.v1.MembershipActivatedEvent", "MembershipActivatedEvent", json1);
        assertTrue(p1 instanceof MembershipActivatedEvent);

        MembershipPausedEvent paused = MembershipPausedEvent.newBuilder().setMemberId("mem-2").build();
        String json2 = JsonFormat.printer().print(paused);
        Message p2 = parser.parse("MembershipPausedEvent", json2);
        assertTrue(p2 instanceof MembershipPausedEvent);

        MembershipResumedEvent resumed = MembershipResumedEvent.newBuilder().setMemberId("mem-3").build();
        String json3 = JsonFormat.printer().print(resumed);
        Message p3 = parser.parse("com.gym.proto.events.v1.MembershipResumedEvent", "MembershipResumedEvent", json3);
        assertTrue(p3 instanceof MembershipResumedEvent);

        MembershipExpiringSoonEvent expiring = MembershipExpiringSoonEvent.newBuilder().setMemberId("mem-4").build();
        String json4 = JsonFormat.printer().print(expiring);
        Message p4 = parser.parse("MembershipExpiringSoonEvent", json4);
        assertTrue(p4 instanceof MembershipExpiringSoonEvent);

        MembershipExpiredEvent expired = MembershipExpiredEvent.newBuilder().setMemberId("mem-5").build();
        String json5 = JsonFormat.printer().print(expired);
        Message p5 = parser.parse("MembershipExpiredEvent", json5);
        assertTrue(p5 instanceof MembershipExpiredEvent);
    }

    @Test
    void testRegisterSupplierPackageAndEventType() throws Exception {
        parser.registerSupplier("CustomEvent", MembershipActivatedEvent::newBuilder);
        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder().setMemberId("custom-1").build();
        String json = JsonFormat.printer().print(event);
        Message p = parser.parse("CustomEvent", json);
        assertTrue(p instanceof MembershipActivatedEvent);

        parser.registerPackage("com.gym.proto.events.v1");
        parser.registerPackage("com.gym.proto.events.v1.");
        parser.registerPackage(null);
        parser.registerPackage("");

        parser.registerEventType("ReflectEvent", MembershipActivatedEvent.class);
        Message p2 = parser.parse("ReflectEvent", json);
        assertTrue(p2 instanceof MembershipActivatedEvent);
    }

    @Test
    void givenInvalidClassInRegisterEventType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> parser.registerEventType("AbstractEvent", com.google.protobuf.AbstractMessage.class));
    }

    @Test
    void givenUnregisteredFullyQualifiedName_whenParse_findsClassByReflection() throws Exception {
        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder().setMemberId("mem-reflect").build();
        String json = JsonFormat.printer().print(event);

        Message p = parser.parse("com.gym.proto.events.v1.MembershipActivatedEvent", json);
        assertTrue(p instanceof MembershipActivatedEvent);
    }

    @Test
    void givenEmptyCache_whenParseSimpleName_findsByPackageSearch() throws Exception {
        Field field = OutboxPayloadParser.class.getDeclaredField("builderSuppliers");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(parser)).clear();

        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder().setMemberId("mem-pkg").build();
        String json = JsonFormat.printer().print(event);

        Message p = parser.parse("MembershipActivatedEvent", json);
        assertTrue(p instanceof MembershipActivatedEvent);
    }

    @Test
    void givenInvalidPayloadTypeWithValidFallbackEventType_whenParse_fallsBackToEventType() throws Exception {
        MembershipActivatedEvent event = MembershipActivatedEvent.newBuilder().setMemberId("fallback-1").build();
        String json = JsonFormat.printer().print(event);

        Message p = parser.parse("invalid.payload.Type", "MembershipActivatedEvent", json);
        assertTrue(p instanceof MembershipActivatedEvent);
    }

    @Test
    void givenMalformedJsonPayload_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("events.v1.MembershipActivatedEvent", "MembershipActivatedEvent", "invalid-json{"));
    }

    @Test
    void givenInvalidPayloadAndEventType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("invalid.Type1", "invalid.Type2", "{}"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null, null, "{}"));
    }
}
