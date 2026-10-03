package com.clickfeeder.config;

import com.clickfeeder.Settings;
import com.clickfeeder.SettingsStore;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen {
    private SettingsScreen() {
    }

    public static Screen create(Screen parent) {
        Settings settings = SettingsStore.get().copy();
        Settings defaults = new Settings();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("clickfeeder.settings.title"))
            .setSavingRunnable(() -> SettingsStore.save(settings));
        var category = builder.getOrCreateCategory(Component.translatable("clickfeeder.settings.general"));
        var entries = builder.entryBuilder();

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("clickfeeder.settings.enabled"), settings.enabled)
            .setDefaultValue(defaults.enabled)
            .setTooltip(Component.translatable("clickfeeder.settings.enabled.tooltip"))
            .setSaveConsumer(value -> settings.enabled = value)
            .build());

        category.addEntry(entries.startIntSlider(
                Component.translatable("clickfeeder.settings.feedRadius"), settings.feedRadius, 1, 8)
            .setDefaultValue(defaults.feedRadius)
            .setTooltip(Component.translatable("clickfeeder.settings.feedRadius.tooltip"))
            .setSaveConsumer(value -> settings.feedRadius = value)
            .build());

        category.addEntry(entries.startIntSlider(
                Component.translatable("clickfeeder.settings.maxFeedsPerClick"), settings.maxFeedsPerClick, 1, 64)
            .setDefaultValue(defaults.maxFeedsPerClick)
            .setTooltip(Component.translatable("clickfeeder.settings.maxFeedsPerClick.tooltip"))
            .setSaveConsumer(value -> settings.maxFeedsPerClick = value)
            .build());

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("clickfeeder.settings.switchHotbar"), settings.switchHotbar)
            .setDefaultValue(defaults.switchHotbar)
            .setTooltip(Component.translatable("clickfeeder.settings.switchHotbar.tooltip"))
            .setSaveConsumer(value -> settings.switchHotbar = value)
            .build());

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("clickfeeder.settings.restockInventory"), settings.restockInventory)
            .setDefaultValue(defaults.restockInventory)
            .setTooltip(Component.translatable("clickfeeder.settings.restockInventory.tooltip"))
            .setSaveConsumer(value -> settings.restockInventory = value)
            .build());

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("clickfeeder.settings.feedBabies"), settings.feedBabies)
            .setDefaultValue(defaults.feedBabies)
            .setTooltip(Component.translatable("clickfeeder.settings.feedBabies.tooltip"))
            .setSaveConsumer(value -> settings.feedBabies = value)
            .build());

        category.addEntry(entries.startBooleanToggle(
                Component.translatable("clickfeeder.settings.sneakBypass"), settings.sneakBypass)
            .setDefaultValue(defaults.sneakBypass)
            .setTooltip(Component.translatable("clickfeeder.settings.sneakBypass.tooltip"))
            .setSaveConsumer(value -> settings.sneakBypass = value)
            .build());

        return builder.build();
    }
}

