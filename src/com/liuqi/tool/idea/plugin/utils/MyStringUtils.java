package com.liuqi.tool.idea.plugin.utils;

import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Objects;

/**
 * 
 *
 * @author  LiuQi 2019/7/16-9:21
 * @version V1.0
 **/
public class MyStringUtils {
    public static String firstLetterToLower(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }

        return str.replaceFirst(str.substring(0, 1), str.substring(0, 1).toLowerCase());
    }

    public static String toUnderLineStr(String str) {
        return firstLetterToLower(Arrays.stream(Objects.requireNonNull(StringUtils.splitByCharacterTypeCamelCase(str)))
                .reduce((s1, s2) -> s1.toLowerCase().concat("_").concat(s2.toLowerCase())).orElse(""));
    }
}
