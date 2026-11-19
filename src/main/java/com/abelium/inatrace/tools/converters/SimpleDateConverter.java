package com.abelium.inatrace.tools.converters;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.util.StdConverter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

public class SimpleDateConverter {

    public static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd";

    public static class Deserialize extends ValueDeserializer<Instant> {

        @Override
        public Instant deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
            String date = jsonParser.getValueAsString();
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(SIMPLE_DATE_FORMAT);
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                return dateFormat.parse(date).toInstant();
            } catch (ParseException e) {
                throw DatabindException.from(jsonParser,
                        "Failed parsing input date. Date '" + date + "' should be in '" + SIMPLE_DATE_FORMAT + "' format.");
            }
        }
    }

    public static class Serialize extends StdConverter<Instant, String> {
        @Override
        public String convert(Instant instant) {
            return new SimpleDateFormat(SIMPLE_DATE_FORMAT).format(Date.from(instant));
        }
    }
}
