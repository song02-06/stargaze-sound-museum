package com.starmuseum.sign;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「AI 只做减法」的可验证守卫。
 *
 * 产品定义里的承诺是：展签上的每一个字都能在作者原话里找到出处。
 * 这个类就是那句话的可执行版本 —— 它不是文档里的口号，是会失败的测试。
 *
 * 四条规则：
 *   1. 展签不能为空
 *   2. 展签字数不得超过全文（压缩只能是减法）
 *   3. 展签必须是全文的**字符子序列**（只能删字，不能改字、不能换序、不能新造）
 *   4. 展签里的时间、数字、引语必须全部来自全文
 *
 * 第 3 条是最强的一条：只要成立，就数学上保证 AI 没有添加任何新内容。
 * 标点被归一化掉，所以「把逗号改成句号」不会误报 —— 标点不是内容。
 *
 * ⚠️ 一旦有人想放开「允许 AI 扩写」，这条豁免立刻失效：
 *    那时展签必须标注来源，不能再假装是作者自己的话。
 */
public final class SignGuard {

    public record Result(boolean ok, List<String> violations) {
        public String joined() {
            return String.join("；", violations);
        }
    }

    private static final Pattern QUOTED =
            Pattern.compile("[「『\u201c\"]([^」』\u201d\"]{1,16})[」』\u201d\"]");

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** 中文数量 + 单位，用来抓「八岁」「三年前」这类不能被 AI 新造的细节 */
    private static final Pattern CN_NUMBER_WITH_UNIT = Pattern.compile(
            "[零一二三四五六七八九十百千万两]{1,4}(年|月|日|号|岁|点|分|秒|个|次|口|层|只|条|棵|根|遍|回)");

    private SignGuard() {
    }

    public static Result check(String fullText, String signText) {
        List<String> violations = new ArrayList<>(3);

        if (fullText == null || fullText.isBlank()) {
            return new Result(false, List.of("全文是空的，没法判断展签"));
        }
        if (signText == null || signText.isBlank()) {
            return new Result(false, List.of("展签是空的"));
        }

        String full = normalize(fullText);
        String sign = normalize(signText);

        if (sign.length() > full.length()) {
            violations.add("展签比全文还长（%d > %d）—— 压缩只能做减法"
                    .formatted(sign.length(), full.length()));
        }

        int covered = subsequenceCoverage(full, sign);
        if (covered < sign.length()) {
            violations.add("展签里出现了全文没有的内容：…%s…".formatted(window(sign, covered)));
        }

        Set<String> added = entities(signText);
        added.removeAll(entities(fullText));
        if (!added.isEmpty()) {
            violations.add("展签出现了全文没有的时间／数字／引语：" + String.join("、", added));
        }

        return new Result(violations.isEmpty(), List.copyOf(violations));
    }

    /** 违反处前后的窗口，让报错能定位到具体是哪几个字 */
    static String window(String sign, int at) {
        int from = Math.max(0, at - 6);
        int to = Math.min(sign.length(), at + 6);
        return sign.substring(from, to);
    }

    /**
     * 去掉空白与标点。保留汉字、字母、数字。
     * 标点不是内容，所以 AI 把逗号改成句号不该被判违规。
     */
    public static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                continue;
            }
            int type = Character.getType(c);
            if (type == Character.START_PUNCTUATION
                    || type == Character.END_PUNCTUATION
                    || type == Character.OTHER_PUNCTUATION
                    || type == Character.CONNECTOR_PUNCTUATION
                    || type == Character.CONTROL
                    || type == Character.FORMAT) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** needle 是 haystack 的子序列时返回 needle.length()，否则返回匹配到哪一位为止 */
    public static int subsequenceCoverage(String haystack, String needle) {
        int i = 0;
        for (int j = 0; j < haystack.length() && i < needle.length(); j++) {
            if (haystack.charAt(j) == needle.charAt(i)) {
                i++;
            }
        }
        return i;
    }

    public static Set<String> entities(String s) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(s);
        while (m.find()) {
            out.add(m.group());
        }
        m = CN_NUMBER_WITH_UNIT.matcher(s);
        while (m.find()) {
            out.add(m.group());
        }
        m = QUOTED.matcher(s);
        while (m.find()) {
            out.add("「" + m.group(1) + "」");
        }
        return out;
    }

    /** 字符级子序列判断 */
    public static boolean isSubsequence(String haystack, String needle) {
        return subsequenceCoverage(haystack, needle) == needle.length();
    }
}
