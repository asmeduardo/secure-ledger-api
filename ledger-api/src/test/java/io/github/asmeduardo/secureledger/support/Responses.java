package io.github.asmeduardo.secureledger.support;

import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class Responses {

    private Responses() {}

    public static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T read(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }
}
