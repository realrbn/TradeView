package de.tradeview.client;

import net.minecraft.world.item.trading.MerchantOffer;

/** A null offer denotes a level heading. Public because the consumer is a transformed Minecraft class. */
public record PreviewRow(int level, MerchantOffer offer) {}
