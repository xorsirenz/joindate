package dev.xorsirenz.joindate.paper;

import dev.xorsirenz.joindate.common.JoinDateDatabase;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class JoinDatePaper
        extends JavaPlugin
        implements Listener, TabExecutor {

    private JoinDateDatabase database;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "MMMM d, yyyy 'at' h:mm:ss a");

    @Override
    public void onEnable() {
        try {
            database = new JoinDateDatabase(
                    new File(getDataFolder(),
                            "joindate.db"));
            database.open();

        } catch (Exception exception) {
            getLogger().severe("Could not open JoinDate database.");
            exception.printStackTrace();
            getServer()
                    .getPluginManager()
                    .disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager()
                .registerEvents(this, this);

        registerCommand("jd");
        registerCommand("joindate");

        getLogger().info("JoinDate Paper/Folia enabled.");
    }

    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Missing command: " + name);
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        database.recordJoin(
                player.getUniqueId(),
                player.getName(),
                System.currentTimeMillis());
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!sender.hasPermission(
                "joindate.lookup")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
            return true;
        }

        String playerName = args[0];
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);

        if (onlinePlayer != null) {
            lookupJoinDate(
                    sender,
                    playerName,
                    onlinePlayer.getUniqueId());
            return true;
        }

        database.findUuidByName(playerName, (uuid, error) -> {
            if (error != null) {
                sendMessage(
                        sender,
                        ChatColor.RED
                                + "Database error.");
                error.printStackTrace();
                return;
            }

            if (uuid == null) {
                sendMessage(
                        sender,
                        ChatColor.RED
                                + "No player named "
                                + playerName
                                + " has been recorded.");
                return;
            }

            lookupJoinDate(
                    sender,
                    playerName,
                    uuid);
        });
        return true;
    }

    private void lookupJoinDate(
            CommandSender sender,
            String playerName,
            UUID uuid) {

        database.getFirstJoin(uuid, (timestamp, error) -> {
            if (error != null) {
                sendMessage(
                        sender,
                        ChatColor.RED
                                + "Database error.");
                error.printStackTrace();
                return;
            }

            if (timestamp == null) {
                sendMessage(
                        sender,
                        ChatColor.RED
                                + "No join date has been "
                                + "recorded for "
                                + playerName
                                + ".");
                return;
            }

            String formatted = dateFormat.format(new Date(timestamp));

            sendMessage(
                    sender,
                    ChatColor.GOLD
                            + playerName
                            + ChatColor.GRAY
                            + " first joined on "
                            + ChatColor.WHITE
                            + formatted);
        });
    }

    private void sendMessage(
            CommandSender sender,
            String message) {

        Bukkit.getGlobalRegionScheduler()
                .execute(
                        this,
                        () -> sender.sendMessage(
                                message));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args) {

        if (args.length != 1) {
            return List.of();
        }

        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName()
                    .toLowerCase(Locale.ROOT)
                    .startsWith(partial)) {
                result.add(player.getName());
            }
        }
        return result;
    }
}
