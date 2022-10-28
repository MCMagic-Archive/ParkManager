package us.mcmagic.parkmanager.storage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Created by Marc on 10/11/15
 */
public class Locker {
    private UUID uuid;
    private StorageSize size;
    private Inventory inv;

    public Locker(Player player, StorageSize size, ItemStack[] contents) {
        this.uuid = player.getUniqueId();
        this.size = size;
        inv = Bukkit.createInventory(player, size.getRows() * 9, ChatColor.BLUE + "Your Locker");
        if (contents != null) {
            inv.setContents(contents);
        }
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public StorageSize getSize() {
        return size;
    }

    public Inventory getInventory() {
        return inv;
    }
}