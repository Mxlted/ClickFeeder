package com.clickfeeder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ClickFeeder implements ClientModInitializer {
    public static final String MOD_ID = "clickfeeder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final double FEED_RADIUS = 5.0;
    private static final double FEED_RADIUS_SQR = FEED_RADIUS * FEED_RADIUS;
    private static final int MAX_FEEDS_PER_CLICK = 20;
    private static final int HOTBAR_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;

    @Override
    public void onInitializeClient() {
        LOGGER.info("ClickFeeder initializing...");

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND || !world.isClientSide()) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) {
                return InteractionResult.PASS;
            }

            Minecraft mc = Minecraft.getInstance();
            LocalPlayer localPlayer = mc.player;
            MultiPlayerGameMode gameMode = mc.gameMode;
            if (localPlayer == null || gameMode == null) {
                return InteractionResult.PASS;
            }

            List<Animal> animals = findFeedableAnimals(world, localPlayer, held);
            if (animals.isEmpty()) {
                return InteractionResult.PASS;
            }

            Item foodItem = held.getItem();

            int availableFood = countAvailableFood(localPlayer.getInventory(), foodItem);
            int animalsToFeed = Math.min(animals.size(), Math.min(availableFood, MAX_FEEDS_PER_CLICK));

            if (animalsToFeed == 0) {
                return InteractionResult.PASS;
            }

            List<Integer> foodSlots = findAllFoodSlots(localPlayer.getInventory(), foodItem);

            int feedCount = 0;
            int currentFoodSlotIndex = 0;

            for (Animal animal : animals) {
                if (feedCount >= animalsToFeed) {
                    break;
                }

                if (animal.isRemoved() || !animal.isAlive()) {
                    continue;
                }

                if (animal.distanceToSqr(localPlayer) > FEED_RADIUS_SQR) {
                    continue;
                }

                ItemStack handStack = localPlayer.getMainHandItem();
                if (handStack.isEmpty() || handStack.getItem() != foodItem) {
                    if (currentFoodSlotIndex >= foodSlots.size()) {
                        break;
                    }

                    int nextFoodSlot = foodSlots.get(currentFoodSlotIndex);
                    while (currentFoodSlotIndex < foodSlots.size() &&
                           localPlayer.getInventory().getItem(nextFoodSlot).isEmpty()) {
                        currentFoodSlotIndex++;
                        if (currentFoodSlotIndex < foodSlots.size()) {
                            nextFoodSlot = foodSlots.get(currentFoodSlotIndex);
                        }
                    }

                    if (currentFoodSlotIndex >= foodSlots.size()) {
                        break;
                    }

                    nextFoodSlot = foodSlots.get(currentFoodSlotIndex);

                    if (nextFoodSlot < HOTBAR_SIZE) {
                        switchToSlot(localPlayer, nextFoodSlot);
                    } else {
                        int targetHotbarSlot = findBestHotbarSlot(localPlayer.getInventory(), foodItem);
                        swapInventoryToHotbar(localPlayer, gameMode, nextFoodSlot, targetHotbarSlot);
                        switchToSlot(localPlayer, targetHotbarSlot);
                    }

                    handStack = localPlayer.getMainHandItem();
                    if (handStack.isEmpty() || handStack.getItem() != foodItem) {
                        break;
                    }
                }

                gameMode.interact(localPlayer, animal, new EntityHitResult(animal), InteractionHand.MAIN_HAND);
                feedCount++;

                ItemStack afterInteract = localPlayer.getMainHandItem();
                if (afterInteract.isEmpty() || afterInteract.getItem() != foodItem) {
                    currentFoodSlotIndex++;
                }
            }

            return InteractionResult.FAIL;
        });
    }

    private static List<Animal> findFeedableAnimals(Level world, LocalPlayer player, ItemStack food) {
        AABB box = player.getBoundingBox().inflate(FEED_RADIUS);
        return world.getEntitiesOfClass(Animal.class, box, animal ->
            animal.isAlive()
                && !animal.isRemoved()
                && animal.isFood(food)
                && (animal.isBaby() || !animal.isInLove())
        );
    }

    private static int countAvailableFood(Inventory inv, Item foodItem) {
        int count = 0;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() == foodItem) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static List<Integer> findAllFoodSlots(Inventory inv, Item foodItem) {
        List<Integer> slots = new ArrayList<>();
        int selected = inv.getSelectedSlot();

        if (inv.getItem(selected).getItem() == foodItem) {
            slots.add(selected);
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (i != selected && inv.getItem(i).getItem() == foodItem) {
                slots.add(i);
            }
        }

        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            if (inv.getItem(i).getItem() == foodItem) {
                slots.add(i);
            }
        }

        return slots;
    }

    private static int findBestHotbarSlot(Inventory inv, Item foodItem) {
        int selected = inv.getSelectedSlot();

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (inv.getItem(i).isEmpty()) {
                return i;
            }
        }

        ItemStack handStack = inv.getItem(selected);
        if (handStack.isEmpty() || handStack.getItem() != foodItem) {
            return selected;
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (i != selected && inv.getItem(i).getItem() != foodItem) {
                return i;
            }
        }

        return selected;
    }

    private static void switchToSlot(LocalPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static void swapInventoryToHotbar(LocalPlayer player, MultiPlayerGameMode gameMode, int inventorySlot, int hotbarSlot) {
        int containerId = player.inventoryMenu.containerId;
        int containerSlot = inventorySlot;
        gameMode.handleContainerInput(containerId, containerSlot, hotbarSlot, ContainerInput.SWAP, player);
    }
}
