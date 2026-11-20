package org.dromara.xiangqi.enums;

import lombok.Getter;

@Getter
public enum PieceType {

    KING("K", "帅", "将"),
    ADVISOR("A", "仕", "士"),
    ELEPHANT("E", "相", "象"),
    HORSE("H", "马", "马"),
    ROOK("R", "车", "车"),
    CANNON("C", "炮", "炮"),
    PAWN("P", "兵", "卒");

    private final String code;
    private final String redName;
    private final String blackName;

    PieceType(String code, String redName, String blackName) {
        this.code = code;
        this.redName = redName;
        this.blackName = blackName;
    }

    public String getName(boolean isRed) {
        return isRed ? redName : blackName;
    }

    public static PieceType fromCode(String code) {
        for (PieceType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
