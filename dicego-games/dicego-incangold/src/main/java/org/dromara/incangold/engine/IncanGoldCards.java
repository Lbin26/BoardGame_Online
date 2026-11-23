package org.dromara.incangold.engine;

/**
 * 印加宝藏的牌与灾难种类。纯数据类，不依赖 Spring / Lombok / Jackson，
 * 便于单独 javac 编译跑规则场景测试。
 */
public final class IncanGoldCards {

    private IncanGoldCards() {
    }

    public enum Kind {
        TREASURE, HAZARD, ARTIFACT
    }

    /** 五种灾难，每种 3 张；wire code 供前端显示图标与名称 */
    public enum HazardType {
        FIRE("fire"),
        MUMMY("mummy"),
        ROCK("rock"),
        SNAKE("snake"),
        SPIDER("spider");

        private final String code;

        HazardType(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** 一张探索牌；宝藏牌的余数可变（除不尽时留在牌上，撤退时结算） */
    public static final class Card {
        private final Kind kind;
        private final int value;
        private final HazardType hazard;
        private int remainder;

        private Card(Kind kind, int value, HazardType hazard) {
            this.kind = kind;
            this.value = value;
            this.hazard = hazard;
        }

        public static Card treasure(int value) {
            return new Card(Kind.TREASURE, value, null);
        }

        public static Card hazard(HazardType type) {
            return new Card(Kind.HAZARD, 0, type);
        }

        public static Card artifact(int index) {
            return new Card(Kind.ARTIFACT, index, null);
        }

        public Kind kind() {
            return kind;
        }

        public int value() {
            return value;
        }

        public HazardType hazard() {
            return hazard;
        }

        public int remainder() {
            return remainder;
        }

        public void addRemainder(int n) {
            this.remainder += n;
        }

        public void clearRemainder() {
            this.remainder = 0;
        }
    }
}
