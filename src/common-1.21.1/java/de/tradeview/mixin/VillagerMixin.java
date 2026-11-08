package de.tradeview.mixin;

import de.tradeview.PreviewVillager;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements PreviewVillager {
    @Unique private final Map<Integer, MerchantOffers> tradeview$future = new TreeMap<>();
    @Unique private int tradeview$version;
    @Unique private boolean tradeview$generating;
    @Shadow public abstract VillagerData getVillagerData();
    @Shadow public abstract void setVillagerData(VillagerData data);
    @Shadow @Override protected abstract void updateTrades();
    @Shadow private void updateSpecialPrices(Player player) { throw new AssertionError(); }

    protected VillagerMixin(EntityType<? extends AbstractVillager> type, Level level) { super(type, level); }

    @Override public int tradeview$revision() { return tradeview$version; }

    @Override public Map<Integer, MerchantOffers> tradeview$preview() {
        if (!(level() instanceof ServerLevel)) return Map.of();
        VillagerData original = getVillagerData();
        if (original.getLevel() >= 5 || tradeview$generating) return Map.of();
        MerchantOffers activeOffers = getOffers();
        Player trader = getTradingPlayer();
        AbstractVillagerAccessor access = (AbstractVillagerAccessor) this;
        // Run the actual generator so other mods' updateTrades hooks also contribute.
        // A null trading player prevents contextual offers/prices and merchant packets from
        // entering persistent reservations. Do not use setOffers/setTradingPlayer here.
        tradeview$generating = true;
        try {
            access.tradeview$setTradingPlayerDirect(null);
            for (int level = original.getLevel() + 1; level <= 5; level++) {
                if (tradeview$future.containsKey(level)) continue;
                setVillagerData(original.setLevel(level));
                offers = new MerchantOffers();
                updateTrades();
                tradeview$future.put(level, offers);
            }
        } finally {
            setVillagerData(original);
            offers = activeOffers;
            access.tradeview$setTradingPlayerDirect(trader);
            tradeview$generating = false;
        }
        return Map.copyOf(tradeview$future);
    }

    @Override public Map<Integer, MerchantOffers> tradeview$preview(Player player) {
        Map<Integer, MerchantOffers> reserved = tradeview$preview();
        if (reserved.isEmpty()) return reserved;
        VillagerData original = getVillagerData();
        MerchantOffers activeOffers = offers;
        Player trader = getTradingPlayer();
        AbstractVillagerAccessor access = (AbstractVillagerAccessor) this;
        Map<Integer, MerchantOffers> visible = new TreeMap<>();
        tradeview$generating = true;
        try {
            // updateSpecialPrices accepts the viewing player separately. Keeping the merchant's
            // tradingPlayer null suppresses vanilla's real-menu updates and offer packets.
            access.tradeview$setTradingPlayerDirect(null);
            for (var entry : reserved.entrySet()) {
                setVillagerData(original.setLevel(entry.getKey()));
                offers = entry.getValue().copy();
                updateSpecialPrices(player);
                visible.put(entry.getKey(), offers);
            }
        } finally {
            setVillagerData(original);
            offers = activeOffers;
            access.tradeview$setTradingPlayerDirect(trader);
            tradeview$generating = false;
        }
        // Contextual extras (e.g. Hero of the Village Plus) are never saved or unlocked
        // as unconditional trades. Their original mod re-applies them on real level-up.
        return Map.copyOf(visible);
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void tradeview$unlockReserved(CallbackInfo ci) {
        if (tradeview$generating) return;
        MerchantOffers reserved = tradeview$future.remove(getVillagerData().getLevel());
        if (reserved != null) {
            getOffers().addAll(reserved.copy());
            tradeview$version++;
            Player trader = getTradingPlayer();
            if (trader != null) updateSpecialPrices(trader);
            ci.cancel();
        }
    }

    // Trade Cycling calls setOffers(null) before regenerating the current level.
    // Invalidating here also works without an optional dependency on that mod.
    @Inject(method = "setOffers", at = @At("HEAD"))
    private void tradeview$offersReplaced(MerchantOffers offers, CallbackInfo ci) {
        if (tradeview$generating) return;
        tradeview$future.clear();
        tradeview$version++;
    }

    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void tradeview$professionChanged(VillagerData data, CallbackInfo ci) {
        if (tradeview$generating) return;
        VillagerData old = getVillagerData();
        if (!old.getProfession().equals(data.getProfession()) || !old.getType().equals(data.getType())) {
            tradeview$future.clear();
            tradeview$version++;
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void tradeview$save(CompoundTag tag, CallbackInfo ci) {
        var ops = registryAccess().createSerializationContext(NbtOps.INSTANCE);
        tradeview$future.forEach((level, offers) -> MerchantOffers.CODEC.encodeStart(ops, offers).result()
            .ifPresent(encoded -> tag.put("tradeview:level_" + level, encoded)));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void tradeview$load(CompoundTag tag, CallbackInfo ci) {
        tradeview$future.clear();
        var ops = registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (int level = getVillagerData().getLevel() + 1; level <= 5; level++) {
            int target = level;
            String key = "tradeview:level_" + level;
            if (tag.contains(key)) MerchantOffers.CODEC.parse(ops, tag.get(key)).result()
                .ifPresent(offers -> tradeview$future.put(target, offers));
        }
        tradeview$version++;
    }
}
