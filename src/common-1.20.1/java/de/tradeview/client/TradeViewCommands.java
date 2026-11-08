package de.tradeview.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;

/** The client-side /tradepreview command; each loader registers it with its own command source type. */
public final class TradeViewCommands {
    private TradeViewCommands() {}

    public static <S> LiteralArgumentBuilder<S> create(CommandBuildContext buildContext, BiConsumer<S, Component> feedback) {
        return TradeViewCommands.<S>literal("tradepreview")
            .executes(context -> {
                feedback.accept(context.getSource(), Component.literal(
                    "/tradepreview <on|off> | /tradepreview side <left|right> | /tradepreview config"
                        + " | /tradepreview find <enchantment> [level] | /tradepreview find stop"));
                return 1;
            })
            .then(TradeViewCommands.<S>literal("find")
                .then(TradeViewCommands.<S>literal("stop").executes(context -> {
                    feedback.accept(context.getSource(), TradeFindClient.stop());
                    return 1;
                }))
                .then(TradeViewCommands.<S, Holder.Reference<Enchantment>>argument("enchantment",
                        ResourceArgument.resource(buildContext, Registries.ENCHANTMENT))
                    .executes(context -> {
                        // Without a level: level 1, which is also the only level of e.g. Mending.
                        feedback.accept(context.getSource(), TradeFindClient.start(enchantment(context), 1));
                        return 1;
                    })
                    .then(TradeViewCommands.<S, Integer>argument("level", IntegerArgumentType.integer(1, 255))
                        .executes(context -> {
                            feedback.accept(context.getSource(), TradeFindClient.start(enchantment(context),
                                IntegerArgumentType.getInteger(context, "level")));
                            return 1;
                        }))))
            .then(TradeViewCommands.<S>literal("config").executes(context -> {
                Minecraft client = Minecraft.getInstance();
                // Queue the screen change until ChatScreen has finished submitting the command.
                client.tell(() -> client.setScreen(new TradeViewSettingsScreen(client.screen)));
                return 1;
            }))
            .then(TradeViewCommands.<S>literal("on").executes(context -> {
                TradeViewConfig.setEnabled(true);
                feedback.accept(context.getSource(), Component.translatable("tradeview.config.enabled",
                    Component.translatable("options.on")));
                return 1;
            }))
            .then(TradeViewCommands.<S>literal("off").executes(context -> {
                TradeViewConfig.setEnabled(false);
                feedback.accept(context.getSource(), Component.translatable("tradeview.config.enabled",
                    Component.translatable("options.off")));
                return 1;
            }))
            .then(TradeViewCommands.<S>literal("side")
                .then(TradeViewCommands.<S>literal("left").executes(context -> {
                    TradeViewConfig.setPreviewInTradeList(true);
                    feedback.accept(context.getSource(), Component.translatable("tradeview.config.previewPosition",
                        Component.translatable("tradeview.config.previewPosition.inline")));
                    return 1;
                }))
                .then(TradeViewCommands.<S>literal("right").executes(context -> {
                    TradeViewConfig.setPreviewInTradeList(false);
                    feedback.accept(context.getSource(), Component.translatable("tradeview.config.previewPosition",
                        Component.translatable("tradeview.config.previewPosition.side")));
                    return 1;
                })));
    }

    private static <S> LiteralArgumentBuilder<S> literal(String name) { return LiteralArgumentBuilder.literal(name); }

    private static <S, T> com.mojang.brigadier.builder.RequiredArgumentBuilder<S, T> argument(
            String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return com.mojang.brigadier.builder.RequiredArgumentBuilder.argument(name, type);
    }

    @SuppressWarnings("unchecked")
    private static <S> Holder.Reference<Enchantment> enchantment(CommandContext<S> context) {
        return context.getArgument("enchantment", Holder.Reference.class);
    }
}
