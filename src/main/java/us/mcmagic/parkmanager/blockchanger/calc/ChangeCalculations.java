package us.mcmagic.parkmanager.blockchanger.calc;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.google.common.base.Stopwatch;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import us.mcmagic.parkmanager.blockchanger.calc.lookup.ConversionLookup;
import us.mcmagic.parkmanager.blockchanger.calc.lookup.SegmentLookup;

import java.util.concurrent.TimeUnit;

/**
 * Created by Marc on 9/9/15
 */
@SuppressWarnings("deprecation")
public class ChangeCalculations {
    // Used to pass around detailed information about chunks
    private static class ChunkInfo {
        public int chunkX;
        public int chunkZ;
        public int chunkMask;
        public int chunkSectionNumber;
        public boolean hasContinous;
        public byte[] data;
        public Player player;
        public int startIndex;
        public int size;
        public int blockSize;
    }

    // Useful Minecraft constants
    private static final int BYTES_PER_NIBBLE_PART = 2048;
    private static final int CHUNK_SEGMENTS = 16;
    private static final int NIBBLES_REQUIRED = 4;
    private static final int BIOME_ARRAY_LENGTH = 256;

    // Used to get a chunk's specific lookup table
    private EventScheduler scheduler;
    private ConversionCache cache;

    public ChangeCalculations(ConversionCache cache, EventScheduler scheduler) {
        this.cache = cache;
        this.scheduler = scheduler;
    }

    public boolean isImportantChunkBulk(PacketContainer packet, Player player) throws FieldAccessException {
        StructureModifier<int[]> intArrays = packet.getSpecificModifier(int[].class);
        int[] x = intArrays.read(0);
        int[] z = intArrays.read(1);

        for (int i = 0; i < x.length; i++) {
            if (Math.abs(x[i] - (((int) player.getLocation().getX()) >> 4)) == 0 &&
                    Math.abs(z[i] - (((int) player.getLocation().getZ())) >> 4) == 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isImportantChunk(PacketContainer packet, Player player) throws FieldAccessException {
        StructureModifier<Integer> ints = packet.getSpecificModifier(int.class);
        int x = ints.read(0);
        int y = ints.read(1);
        return Math.abs(x - (((int) player.getLocation().getX()) >> 4)) == 0 && Math.abs(y -
                (((int) player.getLocation().getZ())) >> 4) == 0;
    }

    // Mimic the ?? operator in C#
    private <T> T getOrDefault(T value, T defaultIfNull) {
        return value != null ? value : defaultIfNull;
    }

    public void translateMapChunk(PacketContainer packet, Player player) throws FieldAccessException, NoSuchFieldException, IllegalAccessException {
        /*
        StructureModifier<Integer> ints = packet.getSpecificModifier(int.class);
        StructureModifier<byte[]> byteArray = packet.getSpecificModifier(byte[].class);
        // Create an info objects
        ChunkInfo info = new ChunkInfo();
        info.player = player;
        info.chunkX = ints.read(0);    // packet.a;
        info.chunkZ = ints.read(1);    // packet.b;
        Field field = ((PacketPlayOutMapChunk) packet.getHandle()).getClass().getDeclaredField("c");
        field.setAccessible(true);
        info.chunkMask = ((PacketPlayOutMapChunk.ChunkMap) field.get(packet.getHandle())).b;
        info.data = ((PacketPlayOutMapChunk.ChunkMap) field.get(packet.getHandle())).a;
        info.hasContinous = getOrDefault(packet.getBooleans().readSafely(0), true);
        info.startIndex = 0;
        if (info.data != null) {
            translateChunkInfoAndObfuscate(info, info.data);
        }*/
    }

    public void translateBlockChange(PacketContainer packet, Player player) throws FieldAccessException {
        StructureModifier<Integer> ints = packet.getSpecificModifier(int.class);
        int x = ints.read(0);
        int y = ints.read(1);
        int z = ints.read(2);
        int blockID = 0;
        int data = 0;
        if (MinecraftReflection.isUsingNetty()) {
            blockID = packet.getBlocks().read(0).getId();
            data = ints.read(3);
        } else {
            blockID = ints.read(3);
            data = ints.read(4);
        }

        // Get the correct table
        ConversionLookup lookup = cache.loadCacheOrDefault(player, x >> 4, y >> 4, z >> 4);

        // Convert using the tables
        int newBlockID = lookup.getBlockLookup(blockID);
        int newData = lookup.getDataLookup(blockID, data);

        // Write the changes
        if (MinecraftReflection.isUsingNetty()) {
            packet.getBlocks().write(0, Material.getMaterial(newBlockID));
            ints.write(3, newData);
        } else {
            ints.write(3, newBlockID);
            ints.write(4, newData);
        }
    }

    public void translateMultiBlockChange(PacketContainer packet, Player player) throws FieldAccessException {
        StructureModifier<byte[]> byteArrays = packet.getSpecificModifier(byte[].class);
        ChunkCoordInt coord = getChunkCoordinate(packet);

        byte[] data = byteArrays.read(0);

        // Get the correct table
        SegmentLookup lookup = cache.loadCacheOrDefault(player, coord.x, coord.z);

        // Each updated block is stored sequentially in 4 byte sized blocks.
        // The content of these bytes are as follows:
        //
        // Byte index:  |       Zero        |       One       |       Two       |      Three        |
        // Bit index:   |  0 - 3  |  4 - 7  |    8   -   15   |   16        -       27   |  28 - 31 |
        // Content:     |    x    |    z    |        y        |         block id         |   data   |
        //
        for (int i = 0; i < data.length; i += 4) {
            int block = ((data[i + 2] << 4) & 0xFFF) |
                    ((data[i + 3] >> 4) & 0xF);
            int info = data[i + 3] & 0xF;
            int chunkY = (data[i + 1] & 0xFF) >> 4;

            if (block >= 0) {
                // Translate and write back the result
                info = lookup.getDataLookup(block, info, chunkY);
                block = lookup.getBlockLookup(block, chunkY);

                data[i + 2] = (byte) ((block >> 4) & 0xFF);
                data[i + 3] = (byte) (((block & 0xF) << 4) | info);
            }
        }
    }

    private ChunkCoordInt getChunkCoordinate(PacketContainer packet) {
        StructureModifier<Integer> ints = packet.getSpecificModifier(int.class);

        if (ints.size() >= 2) {
            return new ChunkCoordInt(ints.read(0), ints.read(1));
        } else {
            // I forgot to add a wrapper - sorry
            return ChunkCoordInt.fromHandle(packet.getModifier().read(0));
        }
    }

    public void translateFallingObject(PacketContainer packet, Player player) throws FieldAccessException {

        StructureModifier<Integer> ints = packet.getSpecificModifier(int.class);

        int type = ints.read(7);
        int data = ints.read(8);

        // Falling object (only block ID)
        if (type == 70) {
            int x = ints.read(1);
            int y = ints.read(2);
            int z = ints.read(3);

            // Get the correct table
            ConversionLookup lookup = cache.loadCacheOrDefault(player, x >> 4, y >> 4, z >> 4);

            data = lookup.getBlockLookup(data);
            ints.write(8, data);
        }
    }

    public void translateDroppedItem(PacketContainer packet, Player player,
                                     EventScheduler scheduler) throws FieldAccessException {

        StructureModifier<Integer> ints = packet.getSpecificModifier(int.class);

        // Minecraft 1.3.2 or lower
        if (ints.size() > 4) {

            int itemsID = ints.read(4);
            int count = ints.read(5);
            int data = ints.read(6);

            ItemStack stack = new ItemStack(itemsID, count, (short) data);
            scheduler.computeItemConversion(new ItemStack[]{stack}, player, false);

            // Make sure it has changed
            if (stack.getTypeId() != itemsID || stack.getAmount() != count || stack.getDurability() != data) {
                ints.write(4, stack.getTypeId());
                ints.write(5, stack.getAmount());
                ints.write(6, (int) stack.getDurability());
            }

            // Minecraft 1.4.2
        } else {
            StructureModifier<ItemStack> stacks = packet.getItemModifier();

            // Very simple
            if (stacks.size() > 0)
                scheduler.computeItemConversion(new ItemStack[]{stacks.read(0)}, player, false);
            else
                throw new IllegalStateException("Unrecognized packet structure.");
        }
    }

    public void translateDroppedItemMetadata(PacketContainer packet, Player player, EventScheduler scheduler) {
        Entity entity = packet.getEntityModifier(player.getWorld()).read(0);

        if (entity instanceof Item) {
            // Great. Get the item from the DataWatcher
            WrappedDataWatcher original = new WrappedDataWatcher(
                    packet.getWatchableCollectionModifier().read(0)
            );

            // Clone it
            WrappedDataWatcher watcher = original.deepClone();

            // Allow mods to convert it and write back the result
            scheduler.computeItemConversion(new ItemStack[]{watcher.getItemStack(10)}, player, false);
            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
        }
    }

    private boolean isChunkLoaded(World world, int x, int z) {
        return world.isChunkLoaded(x, z);
    }

    private void translateChunkInfoAndObfuscate(ChunkInfo info, byte[] returnData) {
        // Compute chunk number
        for (int i = 0; i < CHUNK_SEGMENTS; i++) {
            if ((info.chunkMask & (1 << i)) > 0) {
                info.chunkSectionNumber++;
            }
        }

        // There's no sun/moon in the end or in the nether, so Minecraft doesn't sent any skylight information
        // This optimization was added in 1.4.6. Note that ideally you should get this from the "f" (skylight) field.
        int skylightCount = info.player.getWorld().getEnvironment().equals(World.Environment.NORMAL) ? 1 : 0;

        // The total size of a chunk is the number of blocks sent (depends on the number of sections) multiplied by the
        // amount of bytes per block. This last figure can be calculated by adding together all the data parts:
        //   For any block:
        //    * Block ID          -   8 bits per block (byte)
        //    * Block metadata    -   4 bits per block (nibble)
        //    * Block light array -   4 bits per block
        //   If 'worldProvider.skylight' is TRUE
        //    * Sky light array   -   4 bits per block
        //   If the segment has extra data:
        //    * Add array         -   4 bits per block
        //   Biome array - only if the entire chunk (has continous) is sent:
        //    * Biome array       -   256 bytes
        //
        // A section has 16 * 16 * 16 = 4096 blocks.
        info.size = BYTES_PER_NIBBLE_PART * ((NIBBLES_REQUIRED + skylightCount) * info.chunkSectionNumber) +
                (info.hasContinous ? BIOME_ARRAY_LENGTH : 0);

        info.blockSize = 4096 * info.chunkSectionNumber;

        if (info.startIndex + info.blockSize > info.data.length) {
            return;
        }

        // Make sure the chunk is loaded
        if (isChunkLoaded(info.player.getWorld(), info.chunkX, info.chunkZ)) {
            // Invoke the event
            SegmentLookup baseLookup = cache.getDefaultLookupTable();
            SegmentLookup lookup = scheduler.getChunkConversion(baseLookup, info.player, info.chunkX, info.chunkZ);

            // Save the result to the cache, if it's not the default
            if (!baseLookup.equals(lookup)) {
                cache.saveCache(info.player, info.chunkX, info.chunkZ, lookup);
            } else {
                cache.saveCache(info.player, info.chunkX, info.chunkZ, null);
            }

            translate(lookup, info);
        }
    }

    private void translate(SegmentLookup lookup, ChunkInfo info) {
        // Loop over 16x16x16 chunks in the 16x256x16 column
        int idIndexModifier = 0;
        int idOffset = info.startIndex;
        int dataOffset = idOffset + info.chunkSectionNumber * 4096;

        //Stopwatch watch = new Stopwatch();
        //watch.start();

        for (int i = 0; i < 16; i++) {
            // If the bitmask indicates this chunk is sent
            if ((info.chunkMask & 1 << i) > 0) {

                ConversionLookup view = lookup.getSegmentView(i);

                int relativeIDStart = idIndexModifier * 4096;
                int relativeDataStart = idIndexModifier * 2048;

                //boolean useExtraData = (info.extraMask & (1 << i)) > 0;
                int blockIndex = idOffset + relativeIDStart;
                int dataIndex = dataOffset + relativeDataStart;

                // Stores the extra value
                int output = 0;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int blockID = info.data[blockIndex] & 0xFF;
                            // Transform block
                            info.data[blockIndex] = (byte) view.getBlockLookup(blockID);

                            if ((blockIndex & 0x1) == 0) {
                                int blockData = info.data[dataIndex] & 0xF;

                                // Update the higher nibble
                                output |= view.getDataLookup(blockID, blockData);

                            } else {
                                int blockData = (info.data[dataIndex] >> 4) & 0xF;

                                // Update the lower nibble
                                output |= view.getDataLookup(blockID, blockData) << 4;

                                // Write the result
                                info.data[dataIndex] = (byte) (output & 0xFF);
                                output = 0;
                                dataIndex++;
                            }

                            blockIndex++;
                        }
                    }
                }

                idIndexModifier++;
            }
        }

        //watch.stop();
        //System.out.println(String.format("Processed x: %s, z: %s in %s ms.",
        //			       info.chunkX, info.chunkZ,
        //			       getMilliseconds(watch))
        //);

        // We're done
    }

    // For Minecraft 1.7.2
    private static class ChunkCoordInt {
        private static FieldAccessor COORD_X;
        private static FieldAccessor COORD_Z;

        public final int x;
        public final int z;

        /**
         * Construct a new chunk coordinate.
         *
         * @param x - the x index of the chunk.
         * @param z - the z index of the chunk.
         */
        public ChunkCoordInt(int x, int z) {
            this.x = x;
            this.z = z;
        }

        /**
         * Retrieve a new chunk coord from an object handle.
         *
         * @param handle - the handle.
         * @return The chunk coordinate.
         */
        public static ChunkCoordInt fromHandle(Object handle) {
            if (COORD_X == null || COORD_Z == null) {
                COORD_X = Accessors.getFieldAccessor(handle.getClass(), "x", true);
                COORD_Z = Accessors.getFieldAccessor(handle.getClass(), "z", true);
            }

            return new ChunkCoordInt(
                    (Integer) COORD_X.get(handle),
                    (Integer) COORD_Z.get(handle)
            );
        }
    }

    public static double getMilliseconds(Stopwatch watch) {
        return watch.elapsed(TimeUnit.NANOSECONDS) / 1000000.0;
    }
}