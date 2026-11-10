package com.sma.extension.protocol;

/** Versioned limits, checked both before accepting an upload and while processing actual bytes. */
public record ResourcePolicy(long revision, long jarBytes, long batchBytes, int batchFiles, long sourceBytes,
        long sourceBatchBytes, long expandedBytes, int entries, int archiveDepth, int compressionRatio,
        long pluginBytes, long groupBytes, long repositoryBytes, int retainedVersions, int retentionDays,
        int residentVersions, long minimumFreeBytes, int minimumFreePercent, int parserSeconds,
        int workerSeconds, int workers, int queuedJobs) {
    public static final long MIB = 1024L * 1024;
    public static ResourcePolicy defaults() {
        return new ResourcePolicy(1, 100*MIB, 300*MIB, 20, MIB, 10*MIB, 500*MIB, 20000, 2, 200,
                2048*MIB, 5120*MIB, 20480*MIB, 20, 30, 3, 5120*MIB, 10, 60, 600, 2, 20);
    }
    public ResourcePolicy {
        Checks.require(revision > 0 && sourceBytes > 0 && sourceBytes <= sourceBatchBytes && sourceBatchBytes <= batchBytes
                && jarBytes > 0 && jarBytes <= batchBytes && batchBytes <= pluginBytes && pluginBytes <= groupBytes
                && groupBytes <= repositoryBytes && expandedBytes >= jarBytes, "inconsistent-quota");
        Checks.require(batchFiles > 0 && batchFiles <= 100 && entries > 0 && entries <= 100000 && archiveDepth >= 0 && archiveDepth <= 2
                && compressionRatio > 0 && compressionRatio <= 1000, "invalid-archive-limits");
        Checks.require(retainedVersions > 0 && retentionDays > 0 && residentVersions >= 2 && residentVersions <= 20
                && minimumFreeBytes > 0 && minimumFreePercent >= 5 && minimumFreePercent <= 80, "invalid-retention-limits");
        Checks.require(parserSeconds > 0 && parserSeconds <= 300 && workerSeconds > 0 && workerSeconds <= 3600
                && workers > 0 && workers <= 16 && queuedJobs > 0 && queuedJobs <= 1000, "invalid-worker-limits");
    }
    public long requiredFreeBytes(long totalSpace) {
        Checks.require(totalSpace > 0, "invalid-storage-size");
        return Math.max(minimumFreeBytes, (long)Math.ceil(totalSpace * (minimumFreePercent / 100.0)));
    }
    public void checkSpace(long totalSpace, long usableSpace, long reserved, long incoming) {
        Checks.require(usableSpace >= 0 && reserved >= 0 && incoming >= 0, "invalid-storage-size");
        Checks.require(incoming <= usableSpace && reserved <= usableSpace - incoming
                && usableSpace - incoming - reserved >= requiredFreeBytes(totalSpace), "insufficient-disk-space");
    }
}
