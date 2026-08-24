package my.savingbuddy.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.YearMonth;

/** Persists {@link YearMonth} as "YYYY-MM" text. */
@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth, String> {
    @Override public String convertToDatabaseColumn(YearMonth attribute) { return attribute == null ? null : attribute.toString(); }
    @Override public YearMonth convertToEntityAttribute(String dbData) { return dbData == null ? null : YearMonth.parse(dbData); }
}
