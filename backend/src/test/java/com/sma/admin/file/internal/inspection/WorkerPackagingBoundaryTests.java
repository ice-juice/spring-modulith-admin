package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;

/** ImageIO discovers runtime SPIs globally. Keep worker decoder jars out of the host's production classpath. */
class WorkerPackagingBoundaryTests {
    @Test void webpDecoderIsPinnedToWorkerTestScopeUntilSeparateImagePackagingExists()throws Exception{
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        var pom=factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());var xpath=XPathFactory.newInstance().newXPath();
        String dependency="/project/dependencies/dependency[groupId='com.twelvemonkeys.imageio' and artifactId='imageio-webp']";
        assertThat(xpath.evaluate("count("+dependency+")",pom)).isEqualTo("1");
        assertThat(xpath.evaluate(dependency+"/scope",pom)).isEqualTo("test");
        assertThat(xpath.evaluate(dependency+"/version",pom)).isEqualTo("3.15.3");
    }
}
