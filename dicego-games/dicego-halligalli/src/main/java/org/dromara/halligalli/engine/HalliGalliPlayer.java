package org.dromara.halligalli.engine;

import org.dromara.halligalli.domain.HalliGalliCard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 单个玩家的牌：牌堆（背面朝上，顶部在队首）+ 面前明牌堆（顶部在列表末尾）。
 * 只有明牌堆顶牌计入桌面水果数；牌堆内容对所有人不可见（含本人）。
 */
public class HalliGalliPlayer {

    private final int seatNo;
    private final String name;

    private final Deque<HalliGalliCard> deck = new ArrayDeque<>();

    /** 面前明牌：先翻的在前面，顶牌是最后一个 */
    private final List<HalliGalliCard> faceUp = new ArrayList<>();

    /** 出局标记：轮到自己翻牌却无牌可翻（D4），明牌仍留桌参与计数 */
    private boolean out;

    public HalliGalliPlayer(int seatNo, String name) {
        this.seatNo = seatNo;
        this.name = name;
    }

    public int getSeatNo() {
        return seatNo;
    }

    public String getName() {
        return name;
    }

    public boolean isOut() {
        return out;
    }

    public void setOut(boolean out) {
        this.out = out;
    }

    /** 收牌到牌堆底部（赢牌/被罚给牌都走这里） */
    public void receive(HalliGalliCard card) {
        deck.addLast(card);
    }

    /** 翻牌：取牌堆顶，空则返回 null */
    public HalliGalliCard drawTop() {
        return deck.pollFirst();
    }

    public void pushFaceUp(HalliGalliCard card) {
        faceUp.add(card);
    }

    public HalliGalliCard topCard() {
        return faceUp.isEmpty() ? null : faceUp.get(faceUp.size() - 1);
    }

    /** 付罚牌：优先牌堆顶，牌堆空则从明牌堆顶取（保证有牌就能付） */
    public HalliGalliCard payOne() {
        HalliGalliCard card = deck.pollFirst();
        if (card != null) return card;
        return faceUp.isEmpty() ? null : faceUp.remove(faceUp.size() - 1);
    }

    /** 收走桌面明牌（含出局者的明牌），由结算方转移到赢家 */
    public List<HalliGalliCard> takeFaceUp() {
        List<HalliGalliCard> cards = new ArrayList<>(faceUp);
        faceUp.clear();
        return cards;
    }

    public int deckSize() {
        return deck.size();
    }

    public int faceUpSize() {
        return faceUp.size();
    }

    public int totalCards() {
        return deck.size() + faceUp.size();
    }
}
