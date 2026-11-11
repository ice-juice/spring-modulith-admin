package com.sma.admin.auth.internal.service;
import java.time.Duration;
import org.springframework.stereotype.Service;
import com.sma.admin.common.security.CurrentUser;

/** Separates committed attempt reservation from the enrollment mutation transaction. */
@Service
public class TotpEnrollmentConfirmation {
    private final SecurityChallenges challenges;private final SecurityRateLimit limits;private final TotpEnrollmentService enrollment;private final SecuritySessionGuard sessions;
    public TotpEnrollmentConfirmation(SecurityChallenges challenges,SecurityRateLimit limits,TotpEnrollmentService enrollment,SecuritySessionGuard sessions){this.challenges=challenges;this.limits=limits;this.enrollment=enrollment;this.sessions=sessions;}
    public void confirm(long sessionId,String token,String code,String ip){
        sessions.require(sessionId);limits.claim("ENROLL_IP",ip,60,Duration.ofMinutes(15));
        limits.claim("ENROLL_ACCOUNT",CurrentUser.portal()+":"+CurrentUser.userId(),5,Duration.ofMinutes(15));
        challenges.reserve(token,"TOTP_ENROLL");enrollment.confirm(sessionId,token,code);
    }
}
