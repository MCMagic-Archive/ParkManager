package us.mcmagic.parkmanager.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Commandday implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Bukkit.getWorlds().get(0).setTime(1000);
        sender.sendMessage(ChatColor.GRAY + "Time has been set to "
                + ChatColor.GREEN + "Day.");
        return true;
    }
}