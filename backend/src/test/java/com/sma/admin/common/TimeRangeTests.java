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

	@Test
	void daylightSavingCalendarDaysCanBe23Or25Hours() {
		ZoneId zone = ZoneId.of("America/New_York");
		TimeRange spring = TimeRange.ofLocalDates(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 8), zone);
		TimeRange autumn = TimeRange.ofLocalDates(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), zone);
		assertThat(java.time.Duration.between(spring.start(), spring.endExclusive()).toHours()).isEqualTo(23);
		assertThat(java.time.Duration.between(autumn.start(), autumn.endExclusive()).toHours()).isEqualTo(25);
	}

}
