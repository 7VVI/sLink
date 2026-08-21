package com.shortlink.core.stats;

/**
 * Disruptor 事件槽（RingBuffer 内复用，字段直接读写以降低开销）。
 */
public class ClickEvent {

    public String code;

    public String visitorId;
}
