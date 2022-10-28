package us.mcmagic.parkmanager.show.actions;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import us.mcmagic.parkmanager.show.Show;

public class PulseAction extends ShowAction {
    private Show show;
    private long time;
    public Location loc;

    public PulseAction(Show show, long time, Location loc) {
        super(show, time);
        this.show = show;
        this.time = time;
        this.loc = loc;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void play() {
        Block pre = loc.getBlock();
        final int id = pre.getTypeId();
        final byte data = pre.getData();
        loc.getBlock().setType(Material.REDSTONE_BLOCK);
        show.actions.add(new BlockAction(show, time + 100, loc, id, data));
    }
}
