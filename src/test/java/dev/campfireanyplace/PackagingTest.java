package dev.campfireanyplace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PackagingTest {
    @Test
    void distributionIncludesTheMitLicense() throws Exception {
        var licenses = getClass().getClassLoader().getResources("META-INF/LICENSE");
        boolean found = false;
        while (licenses.hasMoreElements()) {
            try (var stream = licenses.nextElement().openStream()) {
                String contents = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                if (contents.startsWith("MIT License") && contents.contains("twme-ai")) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "CampfireAnyplace MIT license is missing from the distribution");
    }
}
