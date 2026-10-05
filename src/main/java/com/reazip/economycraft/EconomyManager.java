package com.reazip.economycraft;

import com.reazip.economycraft.bank.JointGroups;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.api.v1.BalanceEvents;
import com.reazip.economycraft.api.v1.BalanceMutationResult;
import com.reazip.economycraft.api.v1.MutationSource;
import com.reazip.economycraft.api.v1.PaymentResult;
import com.reazip.economycraft.orders.OrderManager;
import com.reazip.economycraft.auction.AuctionManager;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.EconomyPaths;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ProfileCompat;
import com.reazip.economycraft.util.TransactionLogWriter;
import com.reazip.economycraft.util.UuidLongMapStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;

public class EconomyManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Method NEOFORGE_NAME_LOOKUP = findNeoForgeNameLookup();
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<UUID, Long>>(){}.getType();
    private static final String ECO_BALANCE_OBJECTIVE = "eco_balance";
    private static final int LEADERBOARD_SIZE = 5;
    private static final long SCOREBOARD_SCORE_SCALE = 1000L;
    private static final long LOOKUP_RETRY_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5);
    private static final ExecutorService PROFILE_LOOKUP_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "EconomyCraft-ProfileLookup");
        thread.setDaemon(true);
        return thread;
    });

    private final MinecraftServer server;
    private final Path file;
    private final Path dailyFile;

    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDaily = new ConcurrentHashMap<>();
    /** Players who share finances: each group has one pooled balance, held under its first member. */
    private final JointGroups jointGroups = new JointGroups(balances);
    private final Path groupsFile;
    private final PriceRegistry prices;
    private final BalanceEventDispatcher balanceEvents;
    private final BalanceMutationEngine balanceMutations;
    private final DynamicPriceEngine dynamicPrices;

    private Objective objective;
    private final DeliveryManager deliveries;
    private final AuctionManager auctions;
    private final OrderManager orders;
    private final NotificationManager notifications;
    private final Map<UUID, String> displayed = new ConcurrentHashMap<>();
    private final Set<UUID> scheduledProfileLookups = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loggedUnresolvedNames = ConcurrentHashMap.newKeySet();
    private volatile boolean active = true;
    private volatile List<LeaderboardEntry> leaderboardCache;

    public static final long MAX = 999_999_999_999L;

    public EconomyManager(MinecraftServer server) {
        this.server = server;
        this.balanceEvents = BalanceEventDispatcher.forServer(server);
        Path dataDir = EconomyPaths.dataDir(server);

        this.file = dataDir.resolve("balances.json");
        this.dailyFile = dataDir.resolve("daily.json");
        this.groupsFile = dataDir.resolve("groups.json");

        load();
        loadDaily();
        loadGroups();

        Path logsDir = EconomyPaths.logsDir(server);
        TransactionLogWriter.cleanup(logsDir, EconomyConfig.get().transactionLogRetentionDays);
        TransactionLogger transactionLogger = new TransactionLogger(logsDir, this::getBestName);

        this.balanceMutations = new BalanceMutationEngine(
                balances,
                () -> EconomyConfig.get().startingBalance,
                this::updateLeaderboard,
                () -> {
                    updateLeaderboard();
                    save();
                },
                balanceEvents,
                transactionLogger::onTransfer
        );

        this.deliveries = new DeliveryManager(server);
        this.auctions = new AuctionManager(server, deliveries);
        this.orders = new OrderManager(server, deliveries);
        this.notifications = new NotificationManager(server);
        this.prices = new PriceRegistry(server);
        this.dynamicPrices = new DynamicPriceEngine(dataDir);
        dynamicPrices.refresh(server, balances);

        balanceEvents.register(transactionLogger::onBalanceChanged);

        scheduleProfileLookups(balances.keySet());
        applyScoreboardSettingOnStartup();
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void detach() {
        active = false;
        teardownObjective(server.getScoreboard());
    }

    public void deactivate() {
        active = false;
        BalanceEventDispatcher.release(server);
    }

    private @Nullable String resolveName(MinecraftServer server, UUID id) {
        String localName = resolveLocalName(server, id);
        if (localName != null) return localName;

        scheduleProfileLookup(id);
        return null;
    }

    private static @Nullable String resolveLocalName(MinecraftServer server, UUID id) {
        if (server.isSameThread()) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) return IdentityCompat.of(online).name();
        }

        String cached = safeResolveCachedName(server, id);
        if (cached != null) return cached;

        String loaderCached = getNeoForgeCachedName(id);
        if (loaderCached != null) return loaderCached;
        return null;
    }

    public @Nullable String getBestName(UUID id) {
        return resolveName(server, id);
    }

    public void refreshLeaderboard() {
        updateLeaderboard();
    }

    public UUID tryResolveUuidByName(String name) {
        if (name == null || name.isBlank()) return null;

        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {}

        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();

        UUID match = null;
        for (UUID id : balances.keySet()) {
            String resolved = safeResolveCachedName(server, id);
            if (resolved == null) resolved = getNeoForgeCachedName(id);
            if (resolved == null) continue;
            if (!name.equalsIgnoreCase(resolved)) continue;
            if (match != null && !match.equals(id)) return null;
            match = id;
        }
        return match;
    }

    private static @Nullable String safeResolveCachedName(MinecraftServer server, UUID id) {
        try {
            return ProfileCompat.resolveCachedName(server, id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void scheduleProfileLookup(UUID id) {
        scheduleProfileLookups(List.of(id));
    }

    private void scheduleProfileLookups(Collection<UUID> ids) {
        if (!active) return;

        List<UUID> unresolved = new ArrayList<>();
        for (UUID id : ids) {
            if (resolveLocalName(server, id) != null) continue;

            if (id.version() != 4) {
                logUnresolvedName(id);
            } else if (scheduledProfileLookups.add(id)) {
                unresolved.add(id);
            }
        }
        if (unresolved.isEmpty()) return;

        CompletableFuture
                .supplyAsync(() -> fetchProfiles(unresolved))
                .whenComplete((profiles, error) -> {
                    try {
                        server.execute(() -> finishProfileLookups(unresolved, profiles, error));
                    } catch (RuntimeException ignored) {
                        // The server is already shutting down.
                    }
                });
    }

    private Map<UUID, String> fetchProfiles(Collection<UUID> ids) {
        Map<UUID, String> profiles = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> fetches = new ArrayList<>();
        for (UUID id : ids) {
            fetches.add(CompletableFuture.runAsync(() -> {
                try {
                    Object profile = ProfileCompat.fetchProfile(server, id);
                    if (profile != null) {
                        var identity = IdentityCompat.fromUnknown(profile);
                        if (id.equals(identity.id()) && identity.name() != null && !identity.name().isBlank()) {
                            profiles.put(id, identity.name());
                        }
                    }
                } catch (RuntimeException ignored) {}
            }, PROFILE_LOOKUP_EXECUTOR));
        }
        fetches.forEach(CompletableFuture::join);
        return profiles;
    }

    private void finishProfileLookups(Collection<UUID> requested,
                                      @Nullable Map<UUID, String> profiles,
                                      @Nullable Throwable error) {
        if (!active) return;

        Set<UUID> resolved = new HashSet<>();
        if (error == null && profiles != null) {
            for (var entry : profiles.entrySet()) {
                UUID id = entry.getKey();
                try {
                    ServerPlayer online = server.getPlayerList().getPlayer(id);
                    String name = online != null ? IdentityCompat.of(online).name() : entry.getValue();
                    ProfileCompat.cacheName(server, id, name);
                    resolved.add(id);
                } catch (RuntimeException ignored) {}
            }
        }

        scheduledProfileLookups.removeAll(resolved);

        for (UUID id : requested) {
            if (resolveLocalName(server, id) == null) {
                logUnresolvedName(id);
                if (!resolved.contains(id)) {
                    Set<UUID> scheduledLookups = scheduledProfileLookups;
                    CompletableFuture.delayedExecutor(LOOKUP_RETRY_COOLDOWN_MS, TimeUnit.MILLISECONDS, PROFILE_LOOKUP_EXECUTOR)
                            .execute(() -> scheduledLookups.remove(id));
                }
            }
        }
        updateLeaderboard();
    }

    private void logUnresolvedName(UUID id) {
        if (loggedUnresolvedNames.add(id)) {
            LOGGER.warn("[EconomyCraft] Hiding unresolved player {} from name-based displays.", id);
        }
    }

    private static @Nullable String getNeoForgeCachedName(UUID id) {
        if (NEOFORGE_NAME_LOOKUP == null) return null;
        try {
            Object value = NEOFORGE_NAME_LOOKUP.invoke(null, id);
            return value instanceof String name && !name.isBlank() ? name : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static @Nullable Method findNeoForgeNameLookup() {
        try {
            Class<?> cache = Class.forName("net.neoforged.neoforge.common.UsernameCache");
            return cache.getMethod("getLastKnownUsername", UUID.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public Long getBalance(UUID player, boolean newBalanceIfNonExistent) {
        UUID pool = pool(player);
        if (!newBalanceIfNonExistent) return balances.get(pool);
        return balanceMutations.getBalance(pool);
    }

    public void addMoney(UUID player, long amount) {
        addMoney(player, amount, null);
    }

    public BalanceMutationResult addMoney(UUID player, long amount, @Nullable MutationSource source) {
        return addMoney(player, amount, source, null);
    }

    public BalanceMutationResult addMoney(UUID player, long amount, @Nullable MutationSource source, @Nullable String detail) {
        requireServerThread();
        return balanceMutations.add(pool(player), amount, source, withActor(player, detail));
    }

    public void setMoney(UUID player, long amount) {
        setMoney(player, amount, null);
    }

    public BalanceMutationResult setMoney(UUID player, long amount, @Nullable MutationSource source) {
        return setMoney(player, amount, source, null);
    }

    public BalanceMutationResult setMoney(UUID player, long amount, @Nullable MutationSource source, @Nullable String detail) {
        requireServerThread();
        return balanceMutations.set(pool(player), amount, source, withActor(player, detail));
    }

    public boolean removeMoney(UUID player, long amount) {
        return removeMoney(player, amount, null).successful();
    }

    public BalanceMutationResult removeMoney(UUID player, long amount, @Nullable MutationSource source) {
        return removeMoney(player, amount, source, null);
    }

    public BalanceMutationResult removeMoney(UUID player, long amount, @Nullable MutationSource source, @Nullable String detail) {
        requireServerThread();
        return balanceMutations.remove(pool(player), amount, source, withActor(player, detail));
    }

    public boolean pay(UUID from, UUID to, long amount) {
        return pay(from, to, amount, null).successful();
    }

    public PaymentResult pay(UUID from, UUID to, long amount, @Nullable MutationSource source) {
        return pay(from, to, amount, source, null);
    }

    public PaymentResult pay(UUID from, UUID to, long amount, @Nullable MutationSource source, @Nullable String detail) {
        requireServerThread();
        return balanceMutations.pay(pool(from), pool(to), amount, source, detail);
    }

    public PaymentResult transferMoney(UUID from, UUID to, long debitAmount, long creditAmount,
                                       MutationSource source) {
        return transferMoney(from, to, debitAmount, creditAmount, source, null);
    }

    public PaymentResult transferMoney(UUID from, UUID to, long debitAmount, long creditAmount,
                                       MutationSource source, @Nullable String detail) {
        requireServerThread();
        return balanceMutations.transfer(pool(from), pool(to), debitAmount, creditAmount, source, detail);
    }

    public BalanceEvents getBalanceEvents() {
        return balanceEvents;
    }

    public void load() {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                Map<UUID, Double> map = GSON.fromJson(json, new TypeToken<Map<UUID, Double>>(){}.getType());
                if (map != null) {
                    for (Map.Entry<UUID, Double> e : map.entrySet()) {
                        if (e.getValue() == null) continue;
                        balances.put(e.getKey(), clamp(e.getValue().longValue()));
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    public void save() {
        AsyncFileWriter.writeAsync(file, GSON.toJson(new HashMap<>(balances), TYPE));
        UuidLongMapStore.persist(dailyFile, lastDaily);
        saveGroups();
        dynamicPrices.flush();
    }

    // ---- joint accounts: players who accept an invite share one balance

    private void loadGroups() {
        if (!Files.exists(groupsFile)) return;
        try {
            JsonArray all = GSON.fromJson(Files.readString(groupsFile), JsonArray.class);
            if (all == null) return;
            for (JsonElement entry : all) {
                List<UUID> members = new ArrayList<>();
                for (JsonElement id : entry.getAsJsonArray()) members.add(UUID.fromString(id.getAsString()));
                jointGroups.restore(members);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[EconomyCraft] Failed to read {}", groupsFile, e);
        }
    }

    private void saveGroups() {
        JsonArray all = new JsonArray();
        for (List<UUID> members : jointGroups.snapshot()) {
            JsonArray ids = new JsonArray();
            members.forEach(id -> ids.add(id.toString()));
            all.add(ids);
        }
        AsyncFileWriter.writeAsync(groupsFile, GSON.toJson(all));
    }

    /** The id whose balance this player spends from: themselves, or the head of their joint account. */
    private UUID pool(UUID player) {
        return jointGroups.pool(player);
    }

    public boolean isInJointAccount(UUID player) {
        return jointGroups.isIn(player);
    }

    /** Everyone sharing finances with {@code player}, not counting {@code player}. */
    public List<UUID> jointPartners(UUID player) {
        return jointGroups.partners(player);
    }

    public boolean hasBalance(UUID player) {
        return balances.containsKey(pool(player));
    }

    /** {@code accepter} joins {@code inviter}'s finances; their balances are added together. */
    public JointGroups.JoinResult joinJointAccount(UUID inviter, UUID accepter) {
        requireServerThread();
        JointGroups.JoinResult result = jointGroups.join(inviter, accepter, MAX, balanceMutations::getBalance);
        if (result == JointGroups.JoinResult.JOINED) {
            updateLeaderboard();
            save();
        }
        return result;
    }

    /** Leaves the joint account; the shared balance is split evenly and the leaver takes any remainder. */
    public boolean leaveJointAccount(UUID player) {
        requireServerThread();
        if (!jointGroups.leave(player)) return false;
        updateLeaderboard();
        save();
        return true;
    }

    private @Nullable String withActor(UUID actor, @Nullable String detail) {
        if (!isInJointAccount(actor)) return detail;
        String name = getBestName(actor);
        if (name == null || name.isBlank()) return detail;
        return detail == null ? "by " + name : detail + " (by " + name + ")";
    }

    private void loadDaily() {
        UuidLongMapStore.load(dailyFile, lastDaily);
    }

    private void applyScoreboardSettingOnStartup() {
        Scoreboard board = server.getScoreboard();
        teardownObjective(board);

        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
        }
    }

    static Objective createBalanceObjective(Scoreboard board) {
        return board.addObjective(
                ECO_BALANCE_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal("Balance"),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
        );
    }

    private void ensureObjective(Scoreboard board) {
        if (objective != null) return;

        objective = board.getObjective(ECO_BALANCE_OBJECTIVE);
        if (objective == null) {
            objective = createBalanceObjective(board);
        }
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
    }

    private void teardownObjective(Scoreboard board) {
        removeBalanceObjective(board);
        objective = null;
        displayed.clear();
    }

    static void removeBalanceObjective(Scoreboard board) {
        Objective existing = board.getObjective(ECO_BALANCE_OBJECTIVE);
        if (existing != null) board.removeObjective(existing);
    }

    private void setupObjective() {
        ensureObjective(server.getScoreboard());
        updateLeaderboard();
    }

    private void updateLeaderboard() {
        leaderboardCache = null;

        if (!server.isSameThread()) {
            try {
                server.execute(this::syncScoreboard);
            } catch (RuntimeException ignored) {
            }
            return;
        }

        syncScoreboard();
    }

    private void syncScoreboard() {
        Scoreboard board = server.getScoreboard();

        if (!EconomyConfig.get().scoreboardEnabled) {
            teardownObjective(board);
            return;
        }

        ensureObjective(board);

        syncLeaderboardScores(
                board,
                objective,
                displayed,
                computeLeaderboard(LEADERBOARD_SIZE)
        );
    }

    static void syncLeaderboardScores(
            Scoreboard board,
            Objective objective,
            Map<UUID, String> displayed,
            List<LeaderboardEntry> entries
    ) {
        Map<UUID, String> updated = new HashMap<>();
        for (LeaderboardEntry e : entries) {
            updated.put(e.id(), e.name());
        }

        for (var e : displayed.entrySet()) {
            if (!Objects.equals(e.getValue(), updated.get(e.getKey()))) {
                board.resetSinglePlayerScore(ScoreHolder.forNameOnly(e.getValue()), objective);
            }
        }

        for (LeaderboardEntry e : entries) {
            ScoreAccess score = board.getOrCreatePlayerScore(
                    ScoreHolder.forNameOnly(e.name()),
                    objective
            );
            score.set((int) Math.min(e.balance() / SCOREBOARD_SCORE_SCALE, Integer.MAX_VALUE));
            score.numberFormatOverride(new FixedFormat(Component.literal(EconomyCraft.formatMoneyShort(e.balance()))));
        }

        displayed.clear();
        displayed.putAll(updated);
    }

    private List<LeaderboardEntry> computeLeaderboard(int limit) {
        List<LeaderboardEntry> full = leaderboardCache;
        if (full == null) {
            full = new ArrayList<>();
            for (var entry : balances.entrySet()) {
                String name = resolveName(server, entry.getKey());
                if (name != null && !name.isBlank()) {
                    full.add(new LeaderboardEntry(entry.getKey(), name, entry.getValue()));
                }
            }
            full.sort((a, b) -> {
                int c = Long.compare(b.balance(), a.balance());
                if (c != 0) return c;

                c = String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
                if (c != 0) return c;

                return a.id().compareTo(b.id());
            });
            if (server.isSameThread()) {
                leaderboardCache = full;
            }
        }

        return new ArrayList<>(full.subList(0, Math.min(limit, full.size())));
    }

    public List<LeaderboardEntry> getLeaderboardEntries(int limit) {
        return computeLeaderboard(Math.max(0, limit));
    }

    public @Nullable LeaderboardEntry getLeaderboardEntry(int rank) {
        if (rank < 1) return null;
        List<LeaderboardEntry> top = computeLeaderboard(rank);
        return top.size() < rank ? null : top.get(rank - 1);
    }

    public record LeaderboardEntry(UUID id, String name, long balance) {}

    public boolean toggleScoreboard() {
        EconomyConfig.get().scoreboardEnabled = !EconomyConfig.get().scoreboardEnabled;
        EconomyConfig.save();

        if (EconomyConfig.get().scoreboardEnabled) {
            setupObjective();
        } else {
            teardownObjective(server.getScoreboard());
        }

        return EconomyConfig.get().scoreboardEnabled;
    }

    public AuctionManager getAuctions() {
        return auctions;
    }

    public OrderManager getOrders() {
        return orders;
    }

    public DeliveryManager getDeliveries() {
        return deliveries;
    }

    public NotificationManager getNotifications() {
        return notifications;
    }

    public PriceRegistry getPrices() {
        return prices;
    }

    public void markActive(UUID player) {
        dynamicPrices.markActive(player);
    }

    public void maybeRefreshDynamicPrices() {
        dynamicPrices.maybeRefresh(server, balances);
    }

    public void refreshDynamicPrices() {
        dynamicPrices.refresh(server, balances);
    }

    public double getDynamicPriceMultiplier() {
        return dynamicPrices.getMultiplier();
    }

    public boolean isDynamicPricingActive(String category, boolean itemEnabled) {
        return EconomyConfig.get().dynamicPricesEnabled && prices.isDynamicPricingEnabled(category, itemEnabled);
    }

    public long getEffectiveBuyPrice(long baseBuyPrice, boolean dynamicPricingActive) {
        if (baseBuyPrice <= 0 || !dynamicPricingActive) return baseBuyPrice;
        return dynamicPrices.applyMultiplier(baseBuyPrice);
    }

    public long getEffectiveBuyPrice(long baseBuyPrice, String category, boolean itemEnabled) {
        if (baseBuyPrice <= 0) return baseBuyPrice;
        return getEffectiveBuyPrice(baseBuyPrice, isDynamicPricingActive(category, itemEnabled));
    }

    public long getEffectiveBuyPrice(PriceRegistry.PriceEntry entry) {
        return getEffectiveBuyPrice(entry.unitBuy(), entry.category(), entry.dynamicPriceEnabled());
    }

    public Map<UUID, Long> getBalances() {
        return Map.copyOf(balances);
    }

    public Map<UUID, Long> getBalancesSnapshot() {
        requireServerThread();
        return Map.copyOf(balances);
    }

    public void removePlayer(UUID id) {
        requireServerThread();
        if (isInJointAccount(id)) leaveJointAccount(id);
        balanceMutations.delete(id);
    }

    public boolean claimDaily(UUID player) {
        long today = LocalDate.now().toEpochDay();
        long last = lastDaily.getOrDefault(player, -1L);
        if (last == today) return false;
        BalanceMutationResult result = addMoney(player, EconomyConfig.get().dailyAmount, EconomySources.DAILY_REWARD);
        if (!result.successful()) return false;
        lastDaily.put(player, today);
        save();
        return true;
    }

    public boolean hasClaimedDailyToday(UUID player) {
        return lastDaily.getOrDefault(player, -1L) == LocalDate.now().toEpochDay();
    }

    public void handlePvpKill(ServerPlayer victim, ServerPlayer killer) {
        double pct = Math.min(EconomyConfig.get().pvpBalanceLossPercentage, 1.0);
        if (pct <= 0.0) return;
        if (victim == null || killer == null) return;
        if (victim.getUUID().equals(killer.getUUID())) return;

        long victimBal = getBalance(victim.getUUID(), true);
        if (victimBal <= 0L) return;

        long loss = Math.min((long)Math.floor(pct * victimBal), victimBal);
        if (loss <= 0L) return;
        PaymentResult result = pay(victim.getUUID(), killer.getUUID(), loss, EconomySources.PVP_REWARD);
        if (!result.successful()) return;

        EconomySounds.moneyReceived(killer);
        victim.sendSystemMessage(Component.literal(
                "You lost " + EconomyCraft.formatMoney(loss) + " for being killed by " + killer.getName().getString())
                .withStyle(ChatFormatting.RED));

        killer.sendSystemMessage(Component.literal(
                "You received " + EconomyCraft.formatMoney(loss) + " for killing " + victim.getName().getString())
                .withStyle(ChatFormatting.GREEN));
    }

    private long clamp(long value) {
        return Math.clamp(value, 0, MAX);
    }

    public void requireServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("EconomyCraft API must be called from the server thread");
        }
    }

}
