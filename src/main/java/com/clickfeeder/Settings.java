package com.clickfeeder;

public class Settings {
    public boolean enabled = true;
    public int feedRadius = 5;
    public int maxFeedsPerClick = 20;
    public boolean switchHotbar = true;
    public boolean restockInventory = true;
    public boolean feedBabies = false;
    public boolean sneakBypass = false;

    public Settings copy() {
        Settings copy = new Settings();
        copy.enabled = enabled;
        copy.feedRadius = feedRadius;
        copy.maxFeedsPerClick = maxFeedsPerClick;
        copy.switchHotbar = switchHotbar;
        copy.restockInventory = restockInventory;
        copy.feedBabies = feedBabies;
        copy.sneakBypass = sneakBypass;
        return copy;
    }

    public void validate() {
        feedRadius = Math.clamp(feedRadius, 1, 8);
        maxFeedsPerClick = Math.clamp(maxFeedsPerClick, 1, 64);
    }
}
