package com.sma.admin.mail.internal.service;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component public class MailWorkClock { public Instant now() { return Instant.now(); } }
