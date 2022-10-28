package us.mcmagic.parkmanager.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.mcmagic.parkmanager.ParkManager;

/**
 * Created by Marc on 10/25/15
 */
public class Commandback implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;
        if (!ParkManager.teleportUtil.back(player)) {
            player.sendMessage(ChatColor.GRAY + "No location to teleport back to!");
        }
        return true;
    }
}