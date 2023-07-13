package com.direwolf20.buildinggadgets2.util;

import com.direwolf20.buildinggadgets2.api.gadgets.GadgetModes;
import com.direwolf20.buildinggadgets2.api.gadgets.GadgetTarget;
import com.direwolf20.buildinggadgets2.common.items.BaseGadget;
import com.direwolf20.buildinggadgets2.util.modes.BaseMode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedList;
import java.util.UUID;

public class GadgetNBT {
    final static int undoListSize = 10;

    public static BlockState setGadgetBlockState(ItemStack gadget, BlockState blockState) {
        CompoundTag tag = gadget.getOrCreateTag();
        tag.put("blockstate", NbtUtils.writeBlockState(blockState));
        return blockState;
    }

    public static BlockState getGadgetBlockState(ItemStack gadget) {
        CompoundTag tag = gadget.getTag();
        if (tag == null || !tag.contains("blockstate")) return Blocks.AIR.defaultBlockState();
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("blockstate"));
    }

    public static boolean shouldRayTraceFluid(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean("raytrace_fluid");
    }

    public static void toggleRayTraceFluid(ServerPlayer player, ItemStack stack) {
        stack.getOrCreateTag().putBoolean("raytrace_fluid", !shouldRayTraceFluid(stack));
        //player.displayClientMessage(MessageTranslation.RAYTRACE_FLUID.componentTranslation(shouldRayTraceFluid(stack)).setStyle(Styles.AQUA), true);
    }

    public static LinkedList<UUID> getUndoList(ItemStack gadget) {
        LinkedList<UUID> undoList = new LinkedList<>();
        CompoundTag tag = gadget.getOrCreateTag();
        if (!tag.contains("undolist")) return undoList;
        ListTag undoListTag = tag.getList("undolist", Tag.TAG_COMPOUND);
        for (int i = 0; i < undoListTag.size(); i++) {
            UUID uuid = undoListTag.getCompound(i).getUUID("uuid");
            undoList.offer(uuid);
        }
        return undoList;
    }

    public static void setUndoList(ItemStack gadget, LinkedList<UUID> undoList) {
        CompoundTag tag = gadget.getOrCreateTag();
        ListTag undoListTag = new ListTag();
        for (UUID id : undoList) {
            CompoundTag temptag = new CompoundTag();
            temptag.putUUID("uuid", id);
            undoListTag.add(temptag);
        }
        tag.put("undolist", undoListTag);
    }

    public static void addToUndoList(ItemStack gadget, UUID uuid) {
        LinkedList<UUID> undoList = getUndoList(gadget);
        if (undoList.size() >= undoListSize) {
            undoList.removeFirst();
        }
        undoList.add(uuid);
        setUndoList(gadget, undoList);
    }

    public static UUID popUndoList(ItemStack gadget) {
        LinkedList<UUID> undoList = getUndoList(gadget);
        if (undoList.isEmpty()) return null;
        UUID uuid = undoList.removeLast();
        setUndoList(gadget, undoList);
        return uuid;
    }

    public static boolean toggleSetting(ItemStack stack, String setting) {
        CompoundTag tagCompound = stack.getOrCreateTag();
        tagCompound.putBoolean(setting, !tagCompound.getBoolean(setting));
        return tagCompound.getBoolean(setting);
    }

    public static boolean getSetting(ItemStack stack, String setting) {
        CompoundTag tagCompound = stack.getOrCreateTag();
        return tagCompound.getBoolean(setting);
    }

    public static void setToolRange(ItemStack stack, int range) {
        //Store the tool's range in NBT as an Integer
        CompoundTag tagCompound = stack.getOrCreateTag();
        tagCompound.putInt("range", range);
    }

    public static int getToolRange(ItemStack stack) {
        CompoundTag tagCompound = stack.getOrCreateTag();
        return Mth.clamp(tagCompound.getInt("range"), 1, 15); //TODO Config
    }

    public static boolean getFuzzy(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean("fuzzy");
    }

    public static void toggleFuzzy(Player player, ItemStack stack) {
        stack.getOrCreateTag().putBoolean("fuzzy", !getFuzzy(stack));
        //player.displayClientMessage(MessageTranslation.FUZZY_MODE.componentTranslation(getFuzzy(stack)).setStyle(Styles.AQUA), true);
    }

    /**
     * Safely get a mode based on the given mode id. When one is not present, we'll default to the first one in the list.
     *
     * @param stack the gadget
     * @return the correct mode for the gadget based on the gadget modes registry
     */
    public static BaseMode getMode(ItemStack stack) {
        // Checks if the current item if a gadget, if it's not, throw the game! You shouldn't be using this if you're not a gadget!
        Preconditions.checkArgument(stack.getItem() instanceof BaseGadget, "You can not get a mode of a non-gadget item");

        String mode = stack.getOrCreateTag().getString("mode");
        GadgetTarget gadgetTarget = ((BaseGadget) stack.getItem()).gadgetTarget();

        ImmutableSortedSet<BaseMode> modesForGadget = GadgetModes.INSTANCE.getModesForGadget(gadgetTarget);
        if (mode.isEmpty()) {
            return modesForGadget.first();
        }

        var id = new ResourceLocation(mode);
        return modesForGadget.stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElse(modesForGadget.first());
    }

    public static void setMode(ItemStack gadget, BaseMode mode) {
        gadget.getOrCreateTag().putString("mode", mode.getId().toString());
    }
}
