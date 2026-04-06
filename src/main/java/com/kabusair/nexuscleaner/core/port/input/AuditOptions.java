package com.kabusair.nexuscleaner.core.port.input;

public record AuditOptions(
        boolean includeTestScope,
        boolean checkLatestVersions,
        boolean offline,
        double underuseThreshold
) { }
