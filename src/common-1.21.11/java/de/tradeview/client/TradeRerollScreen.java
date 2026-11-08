package de.tradeview.client;

import net.minecraft.resources.Identifier;

/** Implemented by the merchant screen; shared by the reroll button, its hotkey and the find command. */
public interface TradeRerollScreen {
    /** Requests a reroll if the current villager allows it; returns whether a request was sent. */
    boolean tradeview$reroll();

    /** Asks the server to reroll until an enchanted book with at least this level appears. */
    void tradeview$find(Identifier enchantment, int level);
}
