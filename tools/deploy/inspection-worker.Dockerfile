# Rendered by prepare-inspection-image.py; digest-only inputs, linux/amd64.
FROM --platform=linux/amd64 @@FFPROBE_IMAGE@@ AS native
FROM --platform=linux/amd64 @@JAVA_IMAGE@@
USER 0:0
COPY --from=native --chmod=0555 /ffprobe /opt/inspection-native/ffprobe
COPY --chmod=0444 worker.jar /opt/inspection/worker.jar
COPY --chmod=0555 lib/ /opt/inspection/lib/
# The selected static ffprobe image must work with a completely empty environment.
# No apt/curl/wget, source builds, mutable package repositories or runtime downloads.
RUN mkdir -p /input /output /tmp/inspection && chmod 0555 /input && chmod 0700 /output /tmp/inspection && chown 65532:65532 /output /tmp/inspection
USER 65532:65532
RUN env -i /opt/java/openjdk/bin/java -version 2>&1 | head -n 1 | grep -Fx 'openjdk version "21.0.10" 2026-01-20 LTS'
RUN env -i /opt/java/openjdk/bin/java -version 2>&1 | grep -Fx 'OpenJDK Runtime Environment Temurin-21.0.10+7 (build 21.0.10+7-LTS)'
RUN env -i /opt/inspection-native/ffprobe -version | head -n 1 | grep -E '^ffprobe version 8\.1\.2([ -]|$)'
WORKDIR /tmp/inspection
ENTRYPOINT ["/opt/java/openjdk/bin/java", "-Xmx256m", "-XX:MaxDirectMemorySize=16m", "-XX:ActiveProcessorCount=1", "-XX:+ExitOnOutOfMemoryError", "-XX:-UsePerfData", "-XX:ErrorFile=/tmp/inspection/hs_err_pid%p.log", "-Djava.awt.headless=true", "-Djava.io.tmpdir=/tmp/inspection", "-Dsma.file.worker.ffprobe=/opt/inspection-native/ffprobe", "-cp", "/opt/inspection/worker.jar:/opt/inspection/lib/*", "com.sma.admin.file.internal.inspection.ContentInspectionWorker", "/input/source.blob", "/output", "--hold-output"]
CMD []
