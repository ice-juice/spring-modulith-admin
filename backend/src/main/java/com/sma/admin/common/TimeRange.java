package com.sma.admin.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Half-open UTC time range {@code [start, endExclusive)}. Database times are stored in UTC, while users pick calendar
 * dates in their own time zone; this type converts between the two.
 */
public record TimeRange(Instant start, Instant endExclusive) {

	/**
	 * Calendar days {@code from..to} (inclusive) in the given zone.
	 */
	public static TimeRange ofLocalDates(LocalDate from, LocalDate to, ZoneId zone) {
		return new TimeRange(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
	}

	/**
	 * Calendar days {@code from..to} (inclusive) in the time zone of the current request ({@code X-Time-Zone}).
	 */
	public static TimeRange ofLocalDates(LocalDate from, LocalDate to) {
		return ofLocalDates(from, to, currentZone());
	}

	/**
	 * Time zone of the current request, resolved from the {@code X-Time-Zone} header (falls back to the configured default).
	 */
	public static ZoneId currentZone() {
		return LocaleContextHolder.getTimeZone().toZoneId();
	}

}
