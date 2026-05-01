package com.clickfeeder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ClickFeeder implements ClientModInitializer {
    public static final String MOD_ID = "clickfeeder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final double FEED_RADIUS = 5.0;

    @Override
    public void onInitializeClient() {
        LOGGER.info("ClickFeeder initializing...");

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide() || hand != InteractionHand.MAIN_HAND)
                return InteractionResult.PASS;

            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty())
                return InteractionResult.PASS;

            List<Animal> animals = findFeedableAnimals(world, player, held);
            if (animals.isEmpty())
                return InteractionResult.PASS;

            Minecraft mc = Minecraft.getInstance();
            LocalPlayer localPlayer = mc.player;
            if (localPlayer == null || mc.gameMode == null)
                return InteractionResult.PASS;

            ItemStack foodType = held.copy();

            for (Animal animal : animals) {
                if (animal.isRemoved() || !animal.isAlive()
                    || animal.distanceToSqr(localPlayer) > FEED_RADIUS * FEED_RADIUS)
                    continue;

                ItemStack handStack = localPlayer.getItemInHand(InteractionHand.MAIN_HAND);
                if (!animal.isFood(handStack)) {
                    if (!restockHand(localPlayer, foodType))
                        break;
                    handStack = localPlayer.getItemInHand(InteractionHand.MAIN_HAND);
                    if (!animal.isFood(handStack))
                        break;
                }

                EntityHitResult hit = new EntityHitResult(animal);
                mc.gameMode.interact(localPlayer, animal, hit, InteractionHand.MAIN_HAND);
            }

            return InteractionResult.FAIL;
        });
    }

    private static List<Animal> findFeedableAnimals(net.minecraft.world.level.Level world, net.minecraft.world.entity.player.Player player, ItemStack food) {
        AABB box = player.getBoundingBox().inflate(FEED_RADIUS);
        return world.getEntitiesOfClass(Animal.class, box, mob ->
            mob.isFood(food) && (mob.isBaby() || !mob.isInLove())
        );
    }

    private static boolean restockHand(LocalPlayer player, ItemStack foodType) {
        Inventory inv = player.getInventory();
        int selected = inv.getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            if (i == selected)
                continue;
            if (ItemStack.isSameItemSameComponents(foodType, inv.getItem(i))) {
                inv.setSelectedSlot(i);
                return true;
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!ItemStack.isSameItemSameComponents(foodType, stack))
                continue;

            int target = -1;
            for (int j = 0; j < 9; j++) {
                if (inv.getItem(j).isEmpty()) {
                    target = j;
                    break;
                }
            }
            if (target == -1) {
                ItemStack handStack = inv.getItem(selected);
                if (handStack.isEmpty() || !ItemStack.isSameItemSameComponents(foodType, handStack)) {
                    target = selected;
                }
            }

            if (target != -1) {
                swapSlots(player, i, target);
                inv.setSelectedSlot(target);
                return true;
            }

            break;
        }

        return false;
    }

    private static void swapSlots(LocalPlayer player, int slotA, int slotB) {
        Inventory inv = player.getInventory();
        ItemStack a = inv.getItem(slotA).copy();
        ItemStack b = inv.getItem(slotB).copy();
        inv.setItem(slotB, a);
        inv.setItem(slotA, b);

        int stateId = player.inventoryMenu.getStateId();
        ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
            0, stateId, (short) slotA, (byte) slotB, ContainerInput.SWAP,
            new it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap<>(),
            HashedStack.EMPTY
        );
        player.connection.send(packet);
    }
}
