package com.abelium.inatrace.tools.converters;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class InstantConverter {

    public static class Serializer extends ValueSerializer<Instant> {
        private final DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializationContext serializerContext) {
            gen.writeString(fmt.format(value));
        }
    }
}
