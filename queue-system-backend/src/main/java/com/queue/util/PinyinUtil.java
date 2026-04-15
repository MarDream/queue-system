package com.queue.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for extracting pinyin first letters from Chinese characters.
 * Uses a lookup table for common characters used in business type names.
 */
public class PinyinUtil {

    // Common characters and their pinyin first letters
    private static final Map<Character, Character> CHAR_MAP = new HashMap<>();

    static {
        // Populate with common characters
        String[] entries = {
            "个G", "人R", "业Y", "务W", "对D", "公G", "企Q", "社S",
            "医Y", "疗L", "保B", "险X", "住Z", "房F", "公G", "积J",
            "金J", "老L", "养Y", "退T", "休X", "户H", "口K", "登D",
            "记J", "婚H", "姻Y", "生S", "育Y", "就J", "业Y", "失S",
            "业Y", "工G", "商S", "税S", "水S", "电D", "燃R", "气Q",
            "暖N", "物W", "业Y", "财C", "政Z", "法F", "律L", "诉S",
            "讼S", "仲Z", "裁C", "公G", "证Z", "档D", "案A", "户H",
            "籍J", "签Q", "证Z", "护H", "照Z", "批P", "文W", "许X",
            "可K", "认R", "证Z", "审S", "批P", "核H", "备B", "案A",
            "投T", "诉S", "建Y", "议Y", "咨Z", "询X", "导D", "办B",
            "理L", "缴J", "费F", "查C", "询X", "打D", "印Y", "复F",
            "制Z", "传C", "真Z", "扫S", "描M", "快K", "递D", "邮Y",
            "寄J", "存C", "取Q", "贷D", "款K", "款K", "结J", "算S",
            "汇H", "款K", "转Z", "账Z", "现X", "金J", "支Z", "票P",
            "信X", "用Y", "卡K", "理L", "财C", "基J", "金J", "保B",
            "险X", "基J", "础C", "设S", "施S", "环H", "境J", "卫W",
            "生S", "计J", "划H", "计J", "量L", "质Z", "检J", "安A",
            "全Q", "消X", "防F", "交J", "通T", "运Y", "输S", "文W",
            "化H", "体T", "育Y", "旅L", "游Y", "景J", "点D", "门M",
            "票P", "挂G", "号H", "专Z", "家J", "门M", "诊Z", "手S",
            "术S", "检J", "查C", "化H", "验Y", "输S", "液Y", "注Z",
            "射S", "抽C", "血X", "拍P", "片P", "心X", "电D", "图T",
            "脑N", "电D", "图T", "超C", "声S", "彩C", "超C", "核H",
            "磁C", "共G", "振Z", "CT", "DR", "B超",
            "行X", "政Z", "执Z", "法F", "城C", "管G", "环H", "卫W",
            "林L", "业Y", "农N", "牧M", "渔Y", "水S", "利L", "国G",
            "土T", "规G", "划H", "建J", "设S", "规G", "划H", "用Y",
            "地D", "审S", "批P", "土T", "地D", "出C", "让R", "拍P",
            "卖M", "招Z", "标B", "投T", "标B", "采C", "购G", "资Z",
            "产C", "交J", "易Y", "招Z", "商S", "引Y", "资Z", "投T",
            "资Z", "审S", "批P", "项X", "目M", "核H", "准Z", "备B",
            "案A", "环H", "评P", "能N", "评P", "安A", "评P", "职Z",
            "业Y", "卫W", "生S", "消X", "防F", "验Y", "收S", "备B",
            "案A", "特T", "种Z", "设S", "备B", "登D", "记J", "特T",
            "困K", "补B", "助Z", "低D", "保B", "五W", "保B", "救J",
            "助Z", "残C", "疾J", "人R", "就J", "业Y", "联L", "系X",
            "退T", "役Y", "军J", "人R", "优Y", "抚F", "烈L", "属S",
            "伤S", "残C", "军J", "人R", "复F", "员Y", "退T", "伍W",
            "士S", "兵B", "转Z", "业Y", "安A", "置Z", "就J", "业Y",
            "培P", "训X", "指Z", "导D", "介J", "绍S", "推T", "荐J",
            "就J", "业Y", "创C", "业Y", "贷D", "款K", "小X", "额E",
            "担D", "保B", "贴T", "息X", "补B", "贴T", "补B", "助Z",
            "奖J", "学X", "金J", "助Z", "学X", "贷D", "款K", "生S",
            "源Y", "地D", "认R", "证Z", "毕B", "业Y", "证Z", "报B",
            "到D", "证Z", "户H", "口K", "迁Q", "移Y", "落L", "户H",
            "集J", "体T", "户H", "口K", "人R", "才C", "引Y", "进J",
            "落L", "户H", "人R", "事S", "代D", "理L", "档D", "案A",
            "托T", "管G", "党D", "组Z", "织Z", "关G", "系X", "转Z",
            "接J", "团T", "员Y", "组Z", "织Z", "关G", "系X", "转Z",
            "接J", "少S", "先X", "队D", "员Y", "证Z", "工G", "会H",
            "员Y", "证Z", "独D", "生S", "育Y", "证Z", "光G", "荣R",
            "证Z", "退T", "休X", "证Z", "离L", "休X", "证Z", "医Y",
            "保B", "卡K", "社S", "保B", "卡K", "就J", "业Y", "证Z",
            "失S", "业Y", "证Z", "创C", "业Y", "证Z", "营Y", "业Y",
            "执Z", "照Z", "组Z", "织Z", "机J", "构G", "代D", "码M",
            "税S", "务W", "登D", "记J", "社S", "会H", "统T", "一Y",
            "信X", "用Y", "代D", "码M",
            // Additional common single chars
            "咨Z", "询X", "指Z", "导D", "帮B", "助Z", "投T", "诉S",
            "建Y", "议Y", "意Y", "见J", "信X", "访F", "接J", "待D",
            "服F", "务W", "咨Z", "询X", "导D", "办B", "一Y", "站Z",
            "式S", "服F", "务W", "综Z", "合H", "窗C", "口K", "专Z",
            "项X", "窗C", "口K", "绿L", "色S", "通T", "道D", "爱A",
            "心X", "窗C", "口K", "老L", "弱R", "病B", "残C", "孕Y",
            "优Y", "先X", "窗C", "口K", "军J", "人R", "依Y", "法F",
            "优Y", "先X", "窗C", "口K"
        };

        for (String entry : entries) {
            if (entry.length() >= 2) {
                char c = entry.charAt(0);
                char letter = Character.toUpperCase(entry.charAt(1));
                if (letter >= 'A' && letter <= 'Z') {
                    CHAR_MAP.put(c, letter);
                }
            }
        }
    }

    public static char getPinyinFirstLetter(char c) {
        // English letters pass through
        if (c >= 'a' && c <= 'z') return Character.toUpperCase(c);
        if (c >= 'A' && c <= 'Z') return c;

        // Lookup in map
        Character letter = CHAR_MAP.get(c);
        if (letter != null) return letter;

        // Fallback: try to estimate from Unicode range
        if (c >= 0x4E00 && c <= 0x9FA5) {
            // Very rough estimate based on character code
            // This is not accurate but provides a reasonable fallback
            int offset = c - 0x4E00;
            char[] fallbacks = {'A','B','C','D','E','F','G','H','J','K',
                                'L','M','N','O','P','Q','R','S','T','W',
                                'X','Y','Z'};
            return fallbacks[offset % fallbacks.length];
        }

        if (Character.isLetter(c)) return Character.toUpperCase(c);
        return '?';
    }

    /**
     * Extract pinyin first letters from a Chinese string.
     * E.g., "个人业务" -> "GRYW"
     */
    public static String getPinyinInitials(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            char letter = getPinyinFirstLetter(c);
            if (letter != '?') {
                sb.append(letter);
            }
        }
        return sb.toString();
    }
}
