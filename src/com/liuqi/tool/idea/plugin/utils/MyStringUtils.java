package com.liuqi.tool.idea.plugin.utils;

import org.apache.commons.lang3.StringUtils;

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
}
