package de.tradeview.client;

import de.tradeview.update.UpdateCheckSession;
import de.tradeview.update.UpdateManifest;
import java.net.URI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** Once per game session, on the first singleplayer join; the loader reports joins and the versions. */
public final class UpdateChecker {
    private static final UpdateCheckSession session = new UpdateCheckSession();
    private static String loader, minecraftVersion, installedVersion;

    private UpdateChecker() {}

    public static void initialize(String loaderName, String minecraft, String installed) {
        loader = loaderName;
        minecraftVersion = minecraft;
        installedVersion = installed;
    }

    public static void joined(Minecraft client, ClientPacketListener handler) {
        if (loader == null) return;
        session.onJoin(client.hasSingleplayerServer(), TradeViewConfig.isUpdateCheckEnabled(),
            () -> UpdateManifest.check(loader, minecraftVersion, installedVersion), client::execute,
            () -> TradeViewConfig.isUpdateCheckEnabled() && client.hasSingleplayerServer()
                && client.getConnection() == handler && client.player != null && client.level != null,
            download -> client.gui.hud.getChat().addClientSystemMessage(message(download)));
    }

    public static Component message(URI download) {
        return Component.literal("[Villager Trade Preview: New Version is available. ")
            .append(Component.literal("Update on Curseforge").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(download))))
            .append("]");
    }
}
