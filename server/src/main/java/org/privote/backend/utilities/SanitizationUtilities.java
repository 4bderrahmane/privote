package org.privote.backend.utilities;

import org.springframework.web.util.HtmlUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SanitizationUtilities
{

    private SanitizationUtilities()
    {
    }

    public static String escapeForHtml(String input)
    {
        if (input == null)
        {
            return null;
        }
        String normalized = input.replaceAll("[\\r\\n\\t]", " ");

        return HtmlUtils.htmlEscape(normalized);
    }

    public static Map<String, Object> sanitizeData(Map<String, Object> data)
    {
        if (data == null || data.isEmpty())
        {
            return data;
        }
        Map<String, Object> cleaned = LinkedHashMap.newLinkedHashMap(data.size());
        data.forEach((k, v) ->
        {
            if (v instanceof String s)
            {
                cleaned.put(k, escapeForHtml(s));
            } else
            {
                cleaned.put(k, v);
            }
        });
        return cleaned;
    }
}
