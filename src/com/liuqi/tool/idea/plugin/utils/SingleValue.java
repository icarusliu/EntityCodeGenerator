package com.liuqi.tool.idea.plugin.utils;

/**
 * 单值对象
 * 线程不安全
 *
 * @author  LiuQi 2019/7/2-9:46
 * @version V1.0
 **/
public class SingleValue<T> {
    private T value;

    public boolean isNull() {
        return null == value || "".equals(value.toString().trim());
    }

    public boolean isNotNull() {
        return !isNull();
    }

    public T getValue() {
        return value;
    }

    public T getAndSet(T t) {
        T oldValue = value;
        this.value = t;
        return oldValue;
    }

    public SingleValue<T> setValue(T value) {
        this.value = value;
        return this;
    }
}
