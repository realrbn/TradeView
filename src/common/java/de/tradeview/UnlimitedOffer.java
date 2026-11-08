package de.tradeview;

/** Transient server policy; the offer's original limit stays intact in saved data. */
public interface UnlimitedOffer {
    void tradeview$setUnlimited(boolean enabled);
    boolean tradeview$isUnlimited();
}
