package dev.xorsirenz.joindate.fabric;

import dev.xorsirenz.joindate.common.JoinDateDatabase;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class JoinDateFabric
        implements ModInitializer {

    public static final String MOD_ID = "joindate";
    private JoinDateDatabase database;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "MMMM d, yyyy 'at' h:mm:ss a");

    @Override
    public void onInitialize() {
        File databaseFile = new File("config/joindate/joindate.db");

        try {
            database = new JoinDateDatabase(databaseFile);
            database.open();

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Could not initialize JoinDate database", 
                    exception);
        }

        registerJoinListener();
        registerCommands();

        System.out.println("[JoinDate] Fabric version enabled.");
    }

    private void registerJoinListener() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            UUID uuid = player.getUUID();
            String name = player.getGameProfile().name();
            database.recordJoin(uuid, name, System.currentTimeMillis());
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    registerCommand(dispatcher, "jd");
                    registerCommand(dispatcher, "joindate");
                });
    }

    private void registerCommand(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String commandName) {
        dispatcher.register(literal(commandName).then(
                argument(
                        "player",
                        StringArgumentType.word())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(
                                    context,
                                    "player");
                            return executeLookup(context.getSource(), playerName);
                        })));
    }

    private int executeLookup(
            CommandSourceStack source,
            String playerName) {
        MinecraftServer server = source.getServer();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String currentName = player.getGameProfile().name();
            if (currentName.equalsIgnoreCase(playerName)) {
                lookupJoinDate(
                        source,
                        playerName,
                        player.getUUID());
                return 1;
            }
        }
        database.findUuidByName(playerName, (uuid, error) -> {
            server.execute(() -> {
                if (error != null) {
                    source.sendSystemMessage(
                            Component.literal(
                                    "JoinDate database error."));
                    error.printStackTrace();
                    return;
                }

                if (uuid == null) {
                    source.sendSystemMessage(
                            Component.literal(
                                    "No player named "
                                            + playerName
                                            + " has been recorded."));
                    return;
                }

                lookupJoinDate(source, playerName, uuid);
            });
        });
        return 1;
    }

    private void lookupJoinDate(CommandSourceStack source, String playerName, UUID uuid) {
        database.getFirstJoin(uuid, (timestamp, error) -> {
            source.getServer().execute(() -> {
                if (error != null) {
                    source.sendSystemMessage(
                            Component.literal(
                                    "JoinDate database error."));
                    error.printStackTrace();
                    return;
                }

                if (timestamp == null) {
                    source.sendSystemMessage(
                            Component.literal(
                                    "No join date has been "
                                            + "recorded for "
                                            + playerName));
                    return;
                }

                String formatted = dateFormat.format(new Date(timestamp));
                source.sendSystemMessage(
                        Component.literal(
                                playerName
                                        + " first joined on "
                                        + formatted));
            });
        });
    }
}
