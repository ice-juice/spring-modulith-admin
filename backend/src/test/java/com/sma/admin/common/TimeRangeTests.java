package com.sma.admin.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class TimeRangeTests {

	@Test
	void convertsCalendarDaysOfTheUserZoneToUtc() {
		LocalDate day = LocalDate.of(2026, 11, 10);
		assertThat(TimeRange.ofLocalDates(day, day, ZoneId.of("Asia/Shanghai")))
				.isEqualTo(new TimeRange(Instant.parse("2026-11-09T16:00:00Z"), Instant.parse("2026-11-10T16:00:00Z")));
		assertThat(TimeRange.ofLocalDates(day, day.plusDays(1), ZoneId.of("America/New_York")))
				.isEqualTo(new TimeRange(Instant.parse("2026-11-10T05:00:00Z"), Instant.parse("2026-11-12T05:00:00Z")));
	}

}
