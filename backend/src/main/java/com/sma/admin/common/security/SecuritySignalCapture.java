package com.sma.admin.common.security;

/** Capture at the actual observation point before authentication/context cleanup. Does not enqueue or persist. */
public interface SecuritySignalCapture {
    SecuritySignal capture(SecuritySignal.Fact fact);
}
