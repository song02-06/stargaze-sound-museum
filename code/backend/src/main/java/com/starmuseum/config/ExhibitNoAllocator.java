package com.starmuseum.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 馆藏编号的分配器。
 *
 * 以前是「查当前最大编号 + 1」。这有两个真问题：
 *   1）删掉编号最大的那件投稿，再投一件会**重用旧编号** —— 旧链接指向新内容；
 *   2）两个人同时投稿可能读到同一个最大值，然后撞上唯一约束。
 *
 * 现在改成数据库序列：nextval 是原子的，删号也不会回头。
 * 编号仍然四位起步（0247），超过 9999 自然增长成五位数 —— 展示格式不变。
 *
 * 初始化写在这一处、只用一条 SQL：
 *   建序列（若不存在）→ 和现有最大编号对一次账，序列落后就 restart。
 * 既有编号一律不重排、不重新播种。
 */
@Component
@Order(2)   // 必须在 SeedRunner 之后：种子数据写完之后，max(no) 才是真正的起点
public class ExhibitNoAllocator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExhibitNoAllocator.class);
    private static final String SEQUENCE = "exhibit_no_seq";
    /** 空库的第一件投稿就是 0247，和种子数据的编号段接上 */
    private static final int FIRST_NO = 247;

    private final JdbcTemplate jdbc;
    private volatile boolean ready = false;

    public ExhibitNoAllocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        prepare();
    }

    /** 取下一个编号。第一次调用前会补一次初始化 —— 不依赖启动顺序 */
    public synchronized String next() {
        prepare();
        Long value = jdbc.queryForObject("select next value for " + SEQUENCE, Long.class);
        return format(value == null ? FIRST_NO : value);
    }

    public static String format(long value) {
        return String.format("%04d", value);
    }

    private synchronized void prepare() {
        if (ready) {
            return;
        }
        int wanted = maxExistingNo() + 1;
        jdbc.execute("create sequence if not exists " + SEQUENCE
                + " start with " + wanted + " increment by 1");

        // 序列可能在上一次运行时就建好了，而后来导入的数据编号更高 ——
        // 对一次账，落后就往前重启。这是一次性的对齐，不是每次失败重试。
        Long peek = jdbc.queryForObject("select next value for " + SEQUENCE, Long.class);
        if (peek != null && peek < wanted) {
            jdbc.execute("alter sequence " + SEQUENCE + " restart with " + wanted);
            log.info("馆藏编号序列落后于现有最大编号，已重启到 {}", wanted);
        }
        ready = true;
    }

    private int maxExistingNo() {
        try {
            String max = jdbc.queryForObject("select max(no) from exhibit", String.class);
            if (max == null || max.isBlank()) {
                return FIRST_NO - 1;
            }
            return Integer.parseInt(max.strip());
        } catch (Exception e) {
            log.warn("读不到现有最大编号（{}），按空库处理", e.getMessage());
            return FIRST_NO - 1;
        }
    }
}
