package com.reazip.economycraft.bank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;

/**
 * Who shares finances with whom. A group has one pooled balance, kept in {@code balances} under the group's head
 * (its first member); the other members have no balance entry of their own. Plain Java, so it is easy to test.
 */
public final class JointGroups {
    public enum JoinResult { JOINED, ALREADY_TOGETHER, ACCEPTER_IN_OTHER_ACCOUNT, TOO_MUCH_MONEY }

    private final Map<UUID, Long> balances;
    private final List<List<UUID>> groups = new ArrayList<>();
    private final Map<UUID, UUID> poolOf = new ConcurrentHashMap<>();

    public JointGroups(Map<UUID, Long> balances) {
        this.balances = balances;
    }

    /** The id whose balance this player spends from: themselves, or the head of their joint account. */
    public UUID pool(UUID player) {
        return poolOf.getOrDefault(player, player);
    }

    public boolean isIn(UUID player) {
        return poolOf.containsKey(player);
    }

    /** Everyone sharing finances with {@code player}, not counting {@code player}. */
    public List<UUID> partners(UUID player) {
        List<UUID> members = groupOf(player);
        if (members == null) return List.of();
        List<UUID> partners = new ArrayList<>(members);
        partners.remove(player);
        return partners;
    }

    /** Copies of every group, for saving. */
    public List<List<UUID>> snapshot() {
        List<List<UUID>> copy = new ArrayList<>();
        for (List<UUID> members : groups) copy.add(new ArrayList<>(members));
        return copy;
    }

    /** Adds a saved group (head first). */
    public void restore(List<UUID> members) {
        if (members.size() < 2) return;
        List<UUID> group = new ArrayList<>(members);
        groups.add(group);
        for (UUID member : group) poolOf.put(member, group.get(0));
    }

    /**
     * {@code accepter} joins {@code inviter}'s finances and their balances are added together.
     * {@code balanceOf} reads a raw balance, creating the starting balance if the player has none yet.
     */
    public JoinResult join(UUID inviter, UUID accepter, long maxBalance, ToLongFunction<UUID> balanceOf) {
        if (pool(inviter).equals(pool(accepter))) return JoinResult.ALREADY_TOGETHER;
        if (isIn(accepter)) return JoinResult.ACCEPTER_IN_OTHER_ACCOUNT;

        UUID head = pool(inviter);
        long sum = balanceOf.applyAsLong(head) + balanceOf.applyAsLong(accepter);
        if (sum > maxBalance) return JoinResult.TOO_MUCH_MONEY;

        List<UUID> members = groupOf(inviter);
        if (members == null) {
            restore(List.of(inviter, accepter));
        } else {
            members.add(accepter);
            poolOf.put(accepter, head);
        }
        balances.remove(accepter);
        balances.put(head, sum);
        return JoinResult.JOINED;
    }

    /** Leaves the group. The shared balance is split evenly and the leaver also takes any remainder. */
    public boolean leave(UUID player) {
        List<UUID> members = groupOf(player);
        if (members == null) return false;

        UUID head = members.get(0);
        long total = balances.getOrDefault(head, 0L);
        int count = members.size();
        long share = total / count;
        long leaverShare = total - share * (count - 1);
        long remaining = share * (count - 1);

        members.remove(player);
        poolOf.remove(player);

        if (members.size() < 2) {
            UUID last = members.get(0);
            poolOf.remove(last);
            groups.removeIf(group -> group == members);
            balances.put(last, remaining);
        } else {
            UUID newHead = members.get(0);
            balances.put(newHead, remaining);
            if (!newHead.equals(head)) {
                for (UUID member : members) poolOf.put(member, newHead);
            }
        }
        // Written last: when the leaver was the head this replaces the old pool entry, which has just been moved.
        balances.put(player, leaverShare);
        return true;
    }

    private List<UUID> groupOf(UUID player) {
        UUID head = poolOf.get(player);
        if (head == null) return null;
        for (List<UUID> members : groups) {
            if (members.get(0).equals(head)) return members;
        }
        return null;
    }
}
